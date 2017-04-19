// This file is part of OpenTSDB.
// Copyright (C) 2010-2012  The OpenTSDB Authors.
//
// This program is free software: you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 2.1 of the License, or (at your
// option) any later version.  This program is distributed in the hope that it
// will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
// of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
// General Public License for more details.  You should have received a copy
// of the GNU Lesser General Public License along with this program.  If not,
// see <http://www.gnu.org/licenses/>.
package com.abc.sample.lambda.opentsdb.net.core;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.abc.sample.lambda.opentsdb.net.search.SearchPlugin;
import com.abc.sample.lambda.opentsdb.net.uid.UniqueIdFilterPlugin;
import com.stumbleupon.async.Callback;
import com.stumbleupon.async.Deferred;
import com.stumbleupon.async.DeferredGroupException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hbase.async.AppendRequest;
import org.hbase.async.Bytes;
import org.hbase.async.Bytes.ByteMap;
import org.hbase.async.ClientStats;
import org.hbase.async.DeleteRequest;
import org.hbase.async.GetRequest;
import org.hbase.async.HBaseClient;
import org.hbase.async.HBaseException;
import org.hbase.async.KeyValue;
import org.hbase.async.PutRequest;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;

import com.abc.sample.lambda.opentsdb.net.tree.TreeBuilder;
import com.abc.sample.lambda.openstdb.tsd.RTPublisher;
import com.abc.sample.lambda.openstdb.tsd.StorageExceptionHandler;
import com.abc.sample.lambda.opentsdb.net.uid.NoSuchUniqueId;
import com.abc.sample.lambda.opentsdb.net.uid.NoSuchUniqueName;
import com.abc.sample.lambda.opentsdb.net.uid.UniqueId;
import com.abc.sample.lambda.opentsdb.net.uid.UniqueId.UniqueIdType;
import com.abc.sample.lambda.opentsdb.net.utils.Config;
import com.abc.sample.lambda.opentsdb.net.utils.DateTime;
import com.abc.sample.lambda.opentsdb.net.utils.PluginLoader;
import com.abc.sample.lambda.opentsdb.net.utils.Threads;
import com.abc.sample.lambda.opentsdb.net.meta.Annotation;
import com.abc.sample.lambda.opentsdb.net.meta.MetaDataCache;
import com.abc.sample.lambda.opentsdb.net.meta.TSMeta;
import com.abc.sample.lambda.opentsdb.net.meta.UIDMeta;
import com.abc.sample.lambda.opentsdb.net.query.expression.ExpressionFactory;
import com.abc.sample.lambda.opentsdb.net.query.filter.TagVFilter;
import com.abc.sample.lambda.opentsdb.net.search.SearchQuery;
import com.abc.sample.lambda.opentsdb.net.tools.StartupPlugin;
import com.abc.sample.lambda.opentsdb.net.stats.Histogram;
import com.abc.sample.lambda.opentsdb.net.stats.QueryStats;
import com.abc.sample.lambda.opentsdb.net.stats.StatsCollector;

/**
 * Thread-safe implementation of the TSDB client.
 * <p>
 * This class is the central class of OpenTSDB.  You use it to add new data
 * points or query the database.
 */
public final class TSDB {
  private static final Logger LOG = LoggerFactory.getLogger(TSDB.class);
  
  static final byte[] FAMILY = { 't' };

  /** Charset used to convert Strings to byte arrays and back. */
  private static final Charset CHARSET = Charset.forName("ISO-8859-1");
  private static final String METRICS_QUAL = "metrics";
  private static short METRICS_WIDTH = 3;
  private static final String TAG_NAME_QUAL = "tagk";
  private static short TAG_NAME_WIDTH = 3;
  private static final String TAG_VALUE_QUAL = "tagv";
  private static short TAG_VALUE_WIDTH = 3;

  /** Client for the HBase cluster to use.  */
  final HBaseClient client;

  /** Name of the table in which timeseries are stored.  */
  final byte[] table;
  /** Name of the table in which UID information is stored. */
  final byte[] uidtable;
  /** Name of the table where tree data is stored. */
  final byte[] treetable;
  /** Name of the table where meta data is stored. */
  final byte[] meta_table;

  /** Unique IDs for the metric names. */
  final UniqueId metrics;
  /** Unique IDs for the tag names. */
  final UniqueId tag_names;
  /** Unique IDs for the tag values. */
  final UniqueId tag_values;

  /** Configuration object for all TSDB components */
  final Config config;

  /** Timer used for various tasks such as idle timeouts or query timeouts */
  private final HashedWheelTimer timer;
  
  /**
   * Row keys that need to be compacted.
   * Whenever we write a new data point to a row, we add the row key to this
   * set.  Every once in a while, the compaction thread will go through old
   * row keys and will read re-compact them.
   */
  private final CompactionQueue compactionq;

  /** Search indexer to use if configure */
  private SearchPlugin search = null;

  /** Optional Startup Plugin to use if configured */
  private StartupPlugin startup = null;

  /** Optional real time pulblisher plugin to use if configured */
  private RTPublisher rt_publisher = null;
  
  /** Optional plugin for handling meta data caching and updating */
  private MetaDataCache meta_cache = null;
  
  /** Plugin for dealing with data points that can't be stored */
  private StorageExceptionHandler storage_exception_handler = null;

  /** A filter plugin for allowing or blocking time series */
  private WriteableDataPointFilterPlugin ts_filter;
  
  /** A filter plugin for allowing or blocking UIDs */
  private UniqueIdFilterPlugin uid_filter;
  
  /** Writes rejected by the filter */ 
  private final AtomicLong rejected_dps = new AtomicLong();
  private final AtomicLong rejected_aggregate_dps = new AtomicLong();
  
  /** Datapoints Added */
  private static final AtomicLong datapoints_added = new AtomicLong();

  /**
   * Constructor
   * @param client An initialized HBase client object
   * @param config An initialized configuration object
   * @since 2.1
   */
  public TSDB(final HBaseClient client, final Config config) {
    this.config = config;
    if (client == null) {
      final org.hbase.async.Config async_config;
      if (config.configLocation() != null && !config.configLocation().isEmpty()) {
        try {
          async_config = new org.hbase.async.Config(config.configLocation());
        } catch (final IOException e) {
          throw new RuntimeException("Failed to read the config file: " + 
              config.configLocation(), e);
        }
      } else {
        async_config = new org.hbase.async.Config();
      }
      async_config.overrideConfig("hbase.zookeeper.znode.parent", 
          config.getString("tsd.storage.hbase.zk_basedir"));
      async_config.overrideConfig("hbase.zookeeper.quorum", 
          config.getString("tsd.storage.hbase.zk_quorum"));
      this.client = new HBaseClient(async_config);
    } else {
      this.client = client;
    }
    
    // SALT AND UID WIDTHS
    // Users really wanted this to be set via config instead of having to 
    // compile. Hopefully they know NOT to change these after writing data.
    if (config.hasProperty("tsd.storage.uid.width.metric")) {
      METRICS_WIDTH = config.getShort("tsd.storage.uid.width.metric");
    }
    if (config.hasProperty("tsd.storage.uid.width.tagk")) {
      TAG_NAME_WIDTH = config.getShort("tsd.storage.uid.width.tagk");
    }
    if (config.hasProperty("tsd.storage.uid.width.tagv")) {
      TAG_VALUE_WIDTH = config.getShort("tsd.storage.uid.width.tagv");
    }
    if (config.hasProperty("tsd.storage.max_tags")) {
      Const.setMaxNumTags(config.getShort("tsd.storage.max_tags"));
    }
    if (config.hasProperty("tsd.storage.salt.buckets")) {
      Const.setSaltBuckets(config.getInt("tsd.storage.salt.buckets"));
    }
    if (config.hasProperty("tsd.storage.salt.width")) {
      Const.setSaltWidth(config.getInt("tsd.storage.salt.width"));
    }
    
    table = config.getString("tsd.storage.hbase.data_table").getBytes(CHARSET);
    uidtable = config.getString("tsd.storage.hbase.uid_table").getBytes(CHARSET);
    treetable = config.getString("tsd.storage.hbase.tree_table").getBytes(CHARSET);
    meta_table = config.getString("tsd.storage.hbase.meta_table").getBytes(CHARSET);

    if (config.getBoolean("tsd.core.uid.random_metrics")) {
      metrics = new UniqueId(this, uidtable, METRICS_QUAL, METRICS_WIDTH, true);
    } else {
      metrics = new UniqueId(this, uidtable, METRICS_QUAL, METRICS_WIDTH, false);
    }
    tag_names = new UniqueId(this, uidtable, TAG_NAME_QUAL, TAG_NAME_WIDTH, false);
    tag_values = new UniqueId(this, uidtable, TAG_VALUE_QUAL, TAG_VALUE_WIDTH, false);
    compactionq = new CompactionQueue(this);
    
    if (config.hasProperty("tsd.core.timezone")) {
      DateTime.setDefaultTimezone(config.getString("tsd.core.timezone"));
    }
    
    timer = Threads.newTimer("TSDB Timer");
    
    QueryStats.setEnableDuplicates(
        config.getBoolean("tsd.query.allow_simultaneous_duplicates"));
    
    if (config.getBoolean("tsd.core.preload_uid_cache")) {
      final ByteMap<UniqueId> uid_cache_map = new ByteMap<UniqueId>();
      uid_cache_map.put(METRICS_QUAL.getBytes(CHARSET), metrics);
      uid_cache_map.put(TAG_NAME_QUAL.getBytes(CHARSET), tag_names);
      uid_cache_map.put(TAG_VALUE_QUAL.getBytes(CHARSET), tag_values);
      UniqueId.preloadUidCache(this, uid_cache_map);
    }
    
    if (config.getString("tsd.core.tag.allow_specialchars") != null) {
      Tags.setAllowSpecialChars(config.getString("tsd.core.tag.allow_specialchars"));
    }
    
    // load up the functions that require the TSDB object
    ExpressionFactory.addTSDBFunctions(this);
    
    // set any extra tags from the config for stats
    StatsCollector.setGlobalTags(config);
    
    LOG.debug(config.dumpConfiguration());
  }
  
  /**
   * Constructor
   * @param config An initialized configuration object
   * @since 2.0
   */
  public TSDB(final Config config) {
    this(null, config);
  }
  
  /** @return The data point column family name */
  public static byte[] FAMILY() {
    return FAMILY;
  }

  /**
   * Called by initializePlugins, also used to load startup plugins.
   * @since 2.3
   */
  public static void loadPluginPath(final String plugin_path) {
    if (plugin_path != null && !plugin_path.isEmpty()) {
      try {
        PluginLoader.loadJARs(plugin_path);
      } catch (Exception e) {
        LOG.error("Error loading plugins from plugin path: " + plugin_path, e);
        throw new RuntimeException("Error loading plugins from plugin path: " +
                plugin_path, e);
      }
    }
  }

  /**
   * Should be called immediately after construction to initialize plugins and
   * objects that rely on such. It also moves most of the potential exception
   * throwing code out of the constructor so TSDMain can shutdown clients and
   * such properly.
   * @param init_rpcs Whether or not to initialize RPC plugins as well
   * @throws RuntimeException if the plugin path could not be processed
   * @throws IllegalArgumentException if a plugin could not be initialized
   * @since 2.0
   */
  public void initializePlugins(final boolean init_rpcs) {
    final String plugin_path = config.getString("tsd.core.plugin_path");
    loadPluginPath(plugin_path);

    try {
      TagVFilter.initializeFilterMap(this);
      // @#$@%$%#$ing typed exceptions
    } catch (SecurityException e) {
      throw new RuntimeException("Failed to instantiate filters", e);
    } catch (IllegalArgumentException e) {
      throw new RuntimeException("Failed to instantiate filters", e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Failed to instantiate filters", e);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Failed to instantiate filters", e);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException("Failed to instantiate filters", e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Failed to instantiate filters", e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException("Failed to instantiate filters", e);
    }

    // load the search plugin if enabled
    if (config.getBoolean("tsd.search.enable")) {
      search = PluginLoader.loadSpecificPlugin(
          config.getString("tsd.search.plugin"), SearchPlugin.class);
      if (search == null) {
        throw new IllegalArgumentException("Unable to locate search plugin: " + 
            config.getString("tsd.search.plugin"));
      }
      try {
        search.initialize(this);
      } catch (Exception e) {
        throw new RuntimeException("Failed to initialize search plugin", e);
      }
      LOG.info("Successfully initialized search plugin [" + 
          search.getClass().getCanonicalName() + "] version: " 
          + search.version());
    } else {
      search = null;
    }
    
    // load the real time publisher plugin if enabled
    if (config.getBoolean("tsd.rtpublisher.enable")) {
      rt_publisher = PluginLoader.loadSpecificPlugin(
          config.getString("tsd.rtpublisher.plugin"), RTPublisher.class);
      if (rt_publisher == null) {
        throw new IllegalArgumentException(
            "Unable to locate real time publisher plugin: " + 
            config.getString("tsd.rtpublisher.plugin"));
      }
      try {
        rt_publisher.initialize(this);
      } catch (Exception e) {
        throw new RuntimeException(
            "Failed to initialize real time publisher plugin", e);
      }
      LOG.info("Successfully initialized real time publisher plugin [" + 
          rt_publisher.getClass().getCanonicalName() + "] version: " 
          + rt_publisher.version());
    } else {
      rt_publisher = null;
    }
    
    // load the meta cache plugin if enabled
    if (config.getBoolean("tsd.core.meta.cache.enable")) {
      meta_cache = PluginLoader.loadSpecificPlugin(
          config.getString("tsd.core.meta.cache.plugin"), MetaDataCache.class);
      if (meta_cache == null) {
        throw new IllegalArgumentException(
            "Unable to locate meta cache plugin: " + 
            config.getString("tsd.core.meta.cache.plugin"));
      }
      try {
        meta_cache.initialize(this);
      } catch (Exception e) {
        throw new RuntimeException(
            "Failed to initialize meta cache plugin", e);
      }
      LOG.info("Successfully initialized meta cache plugin [" + 
          meta_cache.getClass().getCanonicalName() + "] version: " 
          + meta_cache.version());
    }
    
    // load the storage exception plugin if enabled
    if (config.getBoolean("tsd.core.storage_exception_handler.enable")) {
      storage_exception_handler = PluginLoader.loadSpecificPlugin(
          config.getString("tsd.core.storage_exception_handler.plugin"), 
          StorageExceptionHandler.class);
      if (storage_exception_handler == null) {
        throw new IllegalArgumentException(
            "Unable to locate storage exception handler plugin: " + 
            config.getString("tsd.core.storage_exception_handler.plugin"));
      }
      try {
        storage_exception_handler.initialize(this);
      } catch (Exception e) {
        throw new RuntimeException(
            "Failed to initialize storage exception handler plugin", e);
      }
      LOG.info("Successfully initialized storage exception handler plugin [" + 
          storage_exception_handler.getClass().getCanonicalName() + "] version: " 
          + storage_exception_handler.version());
    }
    
    // Writeable Data Point Filter
    if (config.getBoolean("tsd.timeseriesfilter.enable")) {
      ts_filter = PluginLoader.loadSpecificPlugin(
          config.getString("tsd.timeseriesfilter.plugin"), 
          WriteableDataPointFilterPlugin.class);
      if (ts_filter == null) {
        throw new IllegalArgumentException(
            "Unable to locate time series filter plugin plugin: " + 
            config.getString("tsd.timeseriesfilter.plugin"));
      }
      try {
        ts_filter.initialize(this);
      } catch (Exception e) {
        throw new RuntimeException(
            "Failed to initialize time series filter plugin", e);
      }
      LOG.info("Successfully initialized time series filter plugin [" + 
          ts_filter.getClass().getCanonicalName() + "] version: " 
          + ts_filter.version());
    }
    
    // UID Filter
    if (config.getBoolean("tsd.uidfilter.enable")) {
      uid_filter = PluginLoader.loadSpecificPlugin(
          config.getString("tsd.uidfilter.plugin"), 
          UniqueIdFilterPlugin.class);
      if (uid_filter == null) {
        throw new IllegalArgumentException(
            "Unable to locate UID filter plugin plugin: " + 
            config.getString("tsd.uidfilter.plugin"));
      }
      try {
        uid_filter.initialize(this);
      } catch (Exception e) {
        throw new RuntimeException(
            "Failed to initialize UID filter plugin", e);
      }
      LOG.info("Successfully initialized UID filter plugin [" + 
          uid_filter.getClass().getCanonicalName() + "] version: " 
          + uid_filter.version());
    }
  }
  

  public final HBaseClient getClient() {
    return this.client;
  }


  public final void setStartupPlugin(final StartupPlugin plugin) { 
    startup = plugin; 
  }
  

  public final StartupPlugin getStartupPlugin() { 
    return startup; 
  }


  public final Config getConfig() {
    return this.config;
  }
  
  /**
   * Returns the storage exception handler. May be null if not enabled
   * @return The storage exception handler
   * @since 2.2
   */
  public final StorageExceptionHandler getStorageExceptionHandler() {
    return storage_exception_handler;
  }

  /**
   * @return the TS filter object, may be null
   * @since 2.3
   */
  public WriteableDataPointFilterPlugin getTSfilter() {
    return ts_filter;
  }
  
  /** 
   * @return The UID filter object, may be null. 
   * @since 2.3 
   */
  public UniqueIdFilterPlugin getUidFilter() {
    return uid_filter;
  }
  
  /**
   * Attempts to find the name for a unique identifier given a type
   * @param type The type of UID
   * @param uid The UID to search for
   * @return The name of the UID object if found
   * @throws IllegalArgumentException if the type is not valid
   * @throws NoSuchUniqueId if the UID was not found
   * @since 2.0
   */
  public Deferred<String> getUidName(final UniqueIdType type, final byte[] uid) {
    if (uid == null) {
      throw new IllegalArgumentException("Missing UID");
    }

    switch (type) {
      case METRIC:
        return this.metrics.getNameAsync(uid);
      case TAGK:
        return this.tag_names.getNameAsync(uid);
      case TAGV:
        return this.tag_values.getNameAsync(uid);
      default:
        throw new IllegalArgumentException("Unrecognized UID type");
    }
  }
  
  public byte[] getUID(final UniqueIdType type, final String name) {
    try {
      return getUIDAsync(type, name).join();
    } catch (NoSuchUniqueName e) {
      throw e;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      LOG.error("Unexpected exception", e);
      throw new RuntimeException(e);
    }
  }
  
  public Deferred<byte[]> getUIDAsync(final UniqueIdType type, final String name) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Missing UID name");
    }
    switch (type) {
      case METRIC:
        return metrics.getIdAsync(name);
      case TAGK:
        return tag_names.getIdAsync(name);
      case TAGV:
        return tag_values.getIdAsync(name);
      default:
        throw new IllegalArgumentException("Unrecognized UID type");
    }
  }
  
  public Deferred<ArrayList<Object>> checkNecessaryTablesExist() {
    final ArrayList<Deferred<Object>> checks = 
      new ArrayList<Deferred<Object>>(2);
    checks.add(client.ensureTableExists(
        config.getString("tsd.storage.hbase.data_table")));
    checks.add(client.ensureTableExists(
        config.getString("tsd.storage.hbase.uid_table")));
    if (config.enable_tree_processing()) {
      checks.add(client.ensureTableExists(
          config.getString("tsd.storage.hbase.tree_table")));
    }
    if (config.enable_realtime_ts() || config.enable_realtime_uid() || 
        config.enable_tsuid_incrementing()) {
      checks.add(client.ensureTableExists(
          config.getString("tsd.storage.hbase.meta_table")));
    }
    return Deferred.group(checks);
  }
  
  /** Number of cache hits during lookups involving UIDs. */
  public int uidCacheHits() {
    return (metrics.cacheHits() + tag_names.cacheHits()
            + tag_values.cacheHits());
  }

  /** Number of cache misses during lookups involving UIDs. */
  public int uidCacheMisses() {
    return (metrics.cacheMisses() + tag_names.cacheMisses()
            + tag_values.cacheMisses());
  }

  /** Number of cache entries currently in RAM for lookups involving UIDs. */
  public int uidCacheSize() {
    return (metrics.cacheSize() + tag_names.cacheSize()
            + tag_values.cacheSize());
  }

  /**
   * Collects the stats and metrics tracked by this instance.
   * @param collector The collector to use.
   */
  public void collectStats(final StatsCollector collector) {
    final byte[][] kinds = { 
        METRICS_QUAL.getBytes(CHARSET), 
        TAG_NAME_QUAL.getBytes(CHARSET), 
        TAG_VALUE_QUAL.getBytes(CHARSET) 
      };
    try {
      final Map<String, Long> used_uids = UniqueId.getUsedUIDs(this, kinds)
        .joinUninterruptibly();
      
      collectUidStats(metrics, collector);
      if (config.getBoolean("tsd.core.uid.random_metrics")) {
        collector.record("uid.ids-used", 0, "kind=" + METRICS_QUAL);
        collector.record("uid.ids-available", 0, "kind=" + METRICS_QUAL);
      } else {
        collector.record("uid.ids-used", used_uids.get(METRICS_QUAL), 
            "kind=" + METRICS_QUAL);
        collector.record("uid.ids-available", 
            (Internal.getMaxUnsignedValueOnBytes(metrics.width()) - 
                used_uids.get(METRICS_QUAL)), "kind=" + METRICS_QUAL);
      }
      
      collectUidStats(tag_names, collector);
      collector.record("uid.ids-used", used_uids.get(TAG_NAME_QUAL), 
          "kind=" + TAG_NAME_QUAL);
      collector.record("uid.ids-available", 
          (Internal.getMaxUnsignedValueOnBytes(tag_names.width()) - 
              used_uids.get(TAG_NAME_QUAL)), 
          "kind=" + TAG_NAME_QUAL);
      
      collectUidStats(tag_values, collector);
      collector.record("uid.ids-used", used_uids.get(TAG_VALUE_QUAL), 
          "kind=" + TAG_VALUE_QUAL);
      collector.record("uid.ids-available", 
          (Internal.getMaxUnsignedValueOnBytes(tag_values.width()) - 
              used_uids.get(TAG_VALUE_QUAL)), "kind=" + TAG_VALUE_QUAL);
      
    } catch (Exception e) {
      throw new RuntimeException("Shouldn't be here", e);
    }
    
    collector.record("uid.filter.rejected", rejected_dps.get(), "kind=raw");
    collector.record("uid.filter.rejected", rejected_aggregate_dps.get(), 
        "kind=aggregate");

    {
      final Runtime runtime = Runtime.getRuntime();
      collector.record("jvm.ramfree", runtime.freeMemory());
      collector.record("jvm.ramused", runtime.totalMemory());
    }

    collector.addExtraTag("class", "IncomingDataPoints");
    try {
      collector.record("hbase.latency", IncomingDataPoints.putlatency, "method=put");
    } finally {
      collector.clearExtraTag("class");
    }

    collector.addExtraTag("class", "TSDB");
    try {
      collector.record("datapoints.added", datapoints_added, "type=all");
    } finally {
      collector.clearExtraTag("class");
    }

    collector.addExtraTag("class", "TsdbQuery");
    try {
      collector.record("hbase.latency", TsdbQuery.scanlatency, "method=scan");
    } finally {
      collector.clearExtraTag("class");
    }
    final ClientStats stats = client.stats();
    collector.record("hbase.root_lookups", stats.rootLookups());
    collector.record("hbase.meta_lookups",
                     stats.uncontendedMetaLookups(), "type=uncontended");
    collector.record("hbase.meta_lookups",
                     stats.contendedMetaLookups(), "type=contended");
    collector.record("hbase.rpcs",
                     stats.atomicIncrements(), "type=increment");
    collector.record("hbase.rpcs", stats.deletes(), "type=delete");
    collector.record("hbase.rpcs", stats.gets(), "type=get");
    collector.record("hbase.rpcs", stats.puts(), "type=put");
    collector.record("hbase.rpcs", stats.appends(), "type=append");
    collector.record("hbase.rpcs", stats.rowLocks(), "type=rowLock");
    collector.record("hbase.rpcs", stats.scannersOpened(), "type=openScanner");
    collector.record("hbase.rpcs", stats.scans(), "type=scan");
    collector.record("hbase.rpcs.batched", stats.numBatchedRpcSent());
    collector.record("hbase.flushes", stats.flushes());
    collector.record("hbase.connections.created", stats.connectionsCreated());
    collector.record("hbase.connections.idle_closed", stats.idleConnectionsClosed());
    collector.record("hbase.nsre", stats.noSuchRegionExceptions());
    collector.record("hbase.nsre.rpcs_delayed",
                     stats.numRpcDelayedDueToNSRE());
    collector.record("hbase.region_clients.open",
        stats.regionClients());
    collector.record("hbase.region_clients.idle_closed",
        stats.idleConnectionsClosed());

    compactionq.collectStats(collector);
    // Collect Stats from Plugins
    if (startup != null) {
      try {
        collector.addExtraTag("plugin", "startup");
        startup.collectStats(collector);
      } finally {
        collector.clearExtraTag("plugin");
      }
    }
    if (rt_publisher != null) {
      try {
        collector.addExtraTag("plugin", "publish");
        rt_publisher.collectStats(collector);
      } finally {
        collector.clearExtraTag("plugin");
      }                        
    }
    if (search != null) {
      try {
        collector.addExtraTag("plugin", "search");
        search.collectStats(collector);
      } finally {
        collector.clearExtraTag("plugin");
      }                        
    }
    if (storage_exception_handler != null) {
      try {
        collector.addExtraTag("plugin", "storageExceptionHandler");
        storage_exception_handler.collectStats(collector);
      } finally {
        collector.clearExtraTag("plugin");
      }
    }
    if (ts_filter != null) {
      try {
        collector.addExtraTag("plugin", "timeseriesFilter");
        ts_filter.collectStats(collector);
      } finally {
        collector.clearExtraTag("plugin");
      }
    }
    if (uid_filter != null) {
      try {
        collector.addExtraTag("plugin", "uidFilter");
        uid_filter.collectStats(collector);
      } finally {
        collector.clearExtraTag("plugin");
      }
    }
  }

  /** Returns a latency histogram for Put RPCs used to store data points. */
  public Histogram getPutLatencyHistogram() {
    return IncomingDataPoints.putlatency;
  }

  /** Returns a latency histogram for Scan RPCs used to fetch data points.  */
  public Histogram getScanLatencyHistogram() {
    return TsdbQuery.scanlatency;
  }

  /**
   * Collects the stats for a {@link UniqueId}.
   * @param uid The instance from which to collect stats.
   * @param collector The collector to use.
   */
  private static void collectUidStats(final UniqueId uid,
                                      final StatsCollector collector) {
    collector.record("uid.cache-hit", uid.cacheHits(), "kind=" + uid.kind());
    collector.record("uid.cache-miss", uid.cacheMisses(), "kind=" + uid.kind());
    collector.record("uid.cache-size", uid.cacheSize(), "kind=" + uid.kind());
    collector.record("uid.random-collisions", uid.randomIdCollisions(), 
        "kind=" + uid.kind());
    collector.record("uid.rejected-assignments", uid.rejectedAssignments(), 
        "kind=" + uid.kind());
  }

  /** @return the width, in bytes, of metric UIDs */
  public static short metrics_width() {
    return METRICS_WIDTH;
  }
  
  /** @return the width, in bytes, of tagk UIDs */
  public static short tagk_width() {
    return TAG_NAME_WIDTH;
  }
  
  /** @return the width, in bytes, of tagv UIDs */
  public static short tagv_width() {
    return TAG_VALUE_WIDTH;
  }
  
  /**
   * Returns a new {@link Query} instance suitable for this TSDB.
   */
  public Query newQuery() {
    return new TsdbQuery(this);
  }

  public WritableDataPoints newDataPoints() {
    return new IncomingDataPoints(this);
  }

  public WritableDataPoints newBatch(String metric, Map<String, String> tags) {
    return new BatchedDataPoints(this, metric, tags);
  }

  public Deferred<Object> addPoint(final String metric,
                                   final long timestamp,
                                   final long value,
                                   final Map<String, String> tags) {
    final byte[] v;
    if (Byte.MIN_VALUE <= value && value <= Byte.MAX_VALUE) {
      v = new byte[] { (byte) value };
    } else if (Short.MIN_VALUE <= value && value <= Short.MAX_VALUE) {
      v = Bytes.fromShort((short) value);
    } else if (Integer.MIN_VALUE <= value && value <= Integer.MAX_VALUE) {
      v = Bytes.fromInt((int) value);
    } else {
      v = Bytes.fromLong(value);
    }

    final short flags = (short) (v.length - 1);  // Just the length.
    return addPointInternal(metric, timestamp, v, tags, flags);
  }

  public Deferred<Object> addPoint(final String metric,
                                   final long timestamp,
                                   final double value,
                                   final Map<String, String> tags) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException("value is NaN or Infinite: " + value
                                         + " for metric=" + metric
                                         + " timestamp=" + timestamp);
    }
    final short flags = Const.FLAG_FLOAT | 0x7;  // A float stored on 8 bytes.
    return addPointInternal(metric, timestamp,
                            Bytes.fromLong(Double.doubleToRawLongBits(value)),
                            tags, flags);
  }

  public Deferred<Object> addPoint(final String metric,
                                   final long timestamp,
                                   final float value,
                                   final Map<String, String> tags) {
    if (Float.isNaN(value) || Float.isInfinite(value)) {
      throw new IllegalArgumentException("value is NaN or Infinite: " + value
                                         + " for metric=" + metric
                                         + " timestamp=" + timestamp);
    }
    final short flags = Const.FLAG_FLOAT | 0x3;  // A float stored on 4 bytes.
    return addPointInternal(metric, timestamp,
                            Bytes.fromInt(Float.floatToRawIntBits(value)),
                            tags, flags);
  }

  private Deferred<Object> addPointInternal(final String metric,
                                            final long timestamp,
                                            final byte[] value,
                                            final Map<String, String> tags,
                                            final short flags) {
    // we only accept positive unix epoch timestamps in seconds or milliseconds
    if (timestamp < 0 || ((timestamp & Const.SECOND_MASK) != 0 && 
        timestamp > 9999999999999L)) {
      throw new IllegalArgumentException((timestamp < 0 ? "negative " : "bad")
          + " timestamp=" + timestamp
          + " when trying to add value=" + Arrays.toString(value) + '/' + flags
          + " to metric=" + metric + ", tags=" + tags);
    }
    IncomingDataPoints.checkMetricAndTags(metric, tags);
    final byte[] row = IncomingDataPoints.rowKeyTemplate(this, metric, tags);
    final long base_time;
    final byte[] qualifier = Internal.buildQualifier(timestamp, flags);
    
    if ((timestamp & Const.SECOND_MASK) != 0) {
      // drop the ms timestamp to seconds to calculate the base timestamp
      base_time = ((timestamp / 1000) - 
          ((timestamp / 1000) % Const.MAX_TIMESPAN));
    } else {
      base_time = (timestamp - (timestamp % Const.MAX_TIMESPAN));
    }
    
    /** Callback executed for chaining filter calls to see if the value
     * should be written or not. */
    final class WriteCB implements Callback<Deferred<Object>, Boolean> {
      @Override
      public Deferred<Object> call(final Boolean allowed) throws Exception {
        if (!allowed) {
          rejected_dps.incrementAndGet();
          return Deferred.fromResult(null);
        }
        
        Bytes.setInt(row, (int) base_time, metrics.width() + Const.SALT_WIDTH());
        RowKey.prefixKeyWithSalt(row);

        Deferred<Object> result = null;
        if (config.enable_appends()) {
          final AppendDataPoints kv = new AppendDataPoints(qualifier, value);
          final AppendRequest point = new AppendRequest(table, row, FAMILY, 
              AppendDataPoints.APPEND_COLUMN_QUALIFIER, kv.getBytes());
          result = client.append(point);
        } else {
          scheduleForCompaction(row, (int) base_time);
          final PutRequest point = new PutRequest(table, row, FAMILY, qualifier, value);
          result = client.put(point);
        }

        // Count all added datapoints, not just those that came in through PUT rpc
        // Will there be others? Well, something could call addPoint programatically right?
        datapoints_added.incrementAndGet();

        // TODO(tsuna): Add a callback to time the latency of HBase and store the
        // timing in a moving Histogram (once we have a class for this).
        
        if (!config.enable_realtime_ts() && !config.enable_tsuid_incrementing() && 
            !config.enable_tsuid_tracking() && rt_publisher == null) {
          return result;
        }
        
        final byte[] tsuid = UniqueId.getTSUIDFromKey(row, METRICS_WIDTH, 
            Const.TIMESTAMP_BYTES);
        
        // if the meta cache plugin is instantiated then tracking goes through it
        if (meta_cache != null) {
          meta_cache.increment(tsuid);
        } else {
          if (config.enable_tsuid_tracking()) {
            if (config.enable_realtime_ts()) {
              if (config.enable_tsuid_incrementing()) {
                TSMeta.incrementAndGetCounter(TSDB.this, tsuid);
              } else {
                TSMeta.storeIfNecessary(TSDB.this, tsuid);
              }
            } else {
              final PutRequest tracking = new PutRequest(meta_table, tsuid, 
                  TSMeta.FAMILY(), TSMeta.COUNTER_QUALIFIER(), Bytes.fromLong(1));
              client.put(tracking);
            }
          }
        }

        if (rt_publisher != null) {
          rt_publisher.sinkDataPoint(metric, timestamp, value, tags, tsuid, flags);
        }
        return result;
      }
      @Override
      public String toString() {
        return "addPointInternal Write Callback";
      }
    }
    
    if (ts_filter != null && ts_filter.filterDataPoints()) {
      return ts_filter.allowDataPoint(metric, timestamp, value, tags, flags)
          .addCallbackDeferring(new WriteCB());
    }
    return Deferred.fromResult(true).addCallbackDeferring(new WriteCB());
  }

  public Deferred<Object> flush() throws HBaseException {
    final class HClientFlush implements Callback<Object, ArrayList<Object>> {
      public Object call(final ArrayList<Object> args) {
        return client.flush();
      }
      public String toString() {
        return "flush HBase client";
      }
    }

    return config.enable_compactions() && compactionq != null
      ? compactionq.flush().addCallback(new HClientFlush())
      : client.flush();
  }

  public Deferred<Object> shutdown() {
    final ArrayList<Deferred<Object>> deferreds = 
      new ArrayList<Deferred<Object>>();
    
    final class FinalShutdown implements Callback<Object, Object> {
      @Override
      public Object call(Object result) throws Exception {
        if (result instanceof Exception) {
          LOG.error("A previous shutdown failed", (Exception)result);
        }
        final Set<Timeout> timeouts = timer.stop();
        // TODO - at some point we should clean these up.
        if (timeouts.size() > 0) {
          LOG.warn("There were " + timeouts.size() + " timer tasks queued");
        }
        LOG.info("Completed shutting down the TSDB");
        return Deferred.fromResult(null);
      }
    }
    
    final class SEHShutdown implements Callback<Object, Object> {
      @Override
      public Object call(Object result) throws Exception {
        if (result instanceof Exception) {
          LOG.error("Shutdown of the HBase client failed", (Exception)result);
        }
        LOG.info("Shutting down storage exception handler plugin: " + 
            storage_exception_handler.getClass().getCanonicalName());
        return storage_exception_handler.shutdown().addBoth(new FinalShutdown());
      }
      @Override
      public String toString() {
        return "SEHShutdown";
      }
    }
    
    final class HClientShutdown implements Callback<Deferred<Object>, ArrayList<Object>> {
      public Deferred<Object> call(final ArrayList<Object> args) {
        if (storage_exception_handler != null) {
          return client.shutdown().addBoth(new SEHShutdown());
        }
        return client.shutdown().addBoth(new FinalShutdown());
      }
      public String toString() {
        return "shutdown HBase client";
      }
    }
    
    final class ShutdownErrback implements Callback<Object, Exception> {
      public Object call(final Exception e) {
        final Logger LOG = LoggerFactory.getLogger(ShutdownErrback.class);
        if (e instanceof DeferredGroupException) {
          final DeferredGroupException ge = (DeferredGroupException) e;
          for (final Object r : ge.results()) {
            if (r instanceof Exception) {
              LOG.error("Failed to shutdown the TSD", (Exception) r);
            }
          }
        } else {
          LOG.error("Failed to shutdown the TSD", e);
        }
        return new HClientShutdown().call(null);
      }
      public String toString() {
        return "shutdown HBase client after error";
      }
    }
    
    final class CompactCB implements Callback<Object, ArrayList<Object>> {
      public Object call(ArrayList<Object> compactions) throws Exception {
        return null;
      }
    }
    
    if (config.enable_compactions()) {
      LOG.info("Flushing compaction queue");
      deferreds.add(compactionq.flush().addCallback(new CompactCB()));
    }
    if (startup != null) {
      LOG.info("Shutting down startup plugin: " +
              startup.getClass().getCanonicalName());
      deferreds.add(startup.shutdown());
    }
    if (search != null) {
      LOG.info("Shutting down search plugin: " + 
          search.getClass().getCanonicalName());
      deferreds.add(search.shutdown());
    }
    if (rt_publisher != null) {
      LOG.info("Shutting down RT plugin: " + 
          rt_publisher.getClass().getCanonicalName());
      deferreds.add(rt_publisher.shutdown());
    }
    if (meta_cache != null) {
      LOG.info("Shutting down meta cache plugin: " + 
          meta_cache.getClass().getCanonicalName());
      deferreds.add(meta_cache.shutdown());
    }
    if (storage_exception_handler != null) {
      LOG.info("Shutting down storage exception handler plugin: " + 
          storage_exception_handler.getClass().getCanonicalName());
      deferreds.add(storage_exception_handler.shutdown());
    }
    if (ts_filter != null) {
      LOG.info("Shutting down time series filter plugin: " + 
          ts_filter.getClass().getCanonicalName());
      deferreds.add(ts_filter.shutdown());
    }
    if (uid_filter != null) {
      LOG.info("Shutting down UID filter plugin: " + 
          uid_filter.getClass().getCanonicalName());
      deferreds.add(uid_filter.shutdown());
    }
    
    // wait for plugins to shutdown before we close the client
    return deferreds.size() > 0
      ? Deferred.group(deferreds).addCallbackDeferring(new HClientShutdown())
          .addErrback(new ShutdownErrback())
      : new HClientShutdown().call(null);
  }

  /**
   * Given a prefix search, returns a few matching metric names.
   * @param search A prefix to search.
   */
  public List<String> suggestMetrics(final String search) {
    return metrics.suggest(search);
  }
  
  /**
   * Given a prefix search, returns matching metric names.
   * @param search A prefix to search.
   * @param max_results Maximum number of results to return.
   * @since 2.0
   */
  public List<String> suggestMetrics(final String search, 
      final int max_results) {
    return metrics.suggest(search, max_results);
  }

  /**
   * Given a prefix search, returns a few matching tag names.
   * @param search A prefix to search.
   */
  public List<String> suggestTagNames(final String search) {
    return tag_names.suggest(search);
  }
  
  /**
   * Given a prefix search, returns matching tagk names.
   * @param search A prefix to search.
   * @param max_results Maximum number of results to return.
   * @since 2.0
   */
  public List<String> suggestTagNames(final String search, 
      final int max_results) {
    return tag_names.suggest(search, max_results);
  }

  /**
   * Given a prefix search, returns a few matching tag values.
   * @param search A prefix to search.
   */
  public List<String> suggestTagValues(final String search) {
    return tag_values.suggest(search);
  }
  
  /**
   * Given a prefix search, returns matching tag values.
   * @param search A prefix to search.
   * @param max_results Maximum number of results to return.
   * @since 2.0
   */
  public List<String> suggestTagValues(final String search, 
      final int max_results) {
    return tag_values.suggest(search, max_results);
  }

  /**
   * Discards all in-memory caches.
   * @since 1.1
   */
  public void dropCaches() {
    metrics.dropCaches();
    tag_names.dropCaches();
    tag_values.dropCaches();
  }

  public byte[] assignUid(final String type, final String name) {
    Tags.validateString(type, name);
    if (type.toLowerCase().equals("metric")) {
      try {
        final byte[] uid = this.metrics.getId(name);
        throw new IllegalArgumentException("Name already exists with UID: " +
            UniqueId.uidToString(uid));
      } catch (NoSuchUniqueName nsue) {
        return this.metrics.getOrCreateId(name);
      }
    } else if (type.toLowerCase().equals("tagk")) {
      try {
        final byte[] uid = this.tag_names.getId(name);
        throw new IllegalArgumentException("Name already exists with UID: " +
            UniqueId.uidToString(uid));
      } catch (NoSuchUniqueName nsue) {
        return this.tag_names.getOrCreateId(name);
      }
    } else if (type.toLowerCase().equals("tagv")) {
      try {
        final byte[] uid = this.tag_values.getId(name);
        throw new IllegalArgumentException("Name already exists with UID: " +
            UniqueId.uidToString(uid));
      } catch (NoSuchUniqueName nsue) {
        return this.tag_values.getOrCreateId(name);
      }
    } else {
      LOG.warn("Unknown type name: " + type);
      throw new IllegalArgumentException("Unknown type name");
    }
  }
  
  public Deferred<Object> deleteUidAsync(final String type, final String name) {
    final UniqueIdType uid_type = UniqueId.stringToUniqueIdType(type);
    switch (uid_type) {
    case METRIC:
      return metrics.deleteAsync(name);
    case TAGK:
      return tag_names.deleteAsync(name);
    case TAGV:
      return tag_values.deleteAsync(name);
    default:
      throw new IllegalArgumentException("Unrecognized UID type: " + uid_type); 
    }
  }
  
  public void renameUid(final String type, final String oldname,
      final String newname) {
    Tags.validateString(type, oldname);
    Tags.validateString(type, newname);
    if (type.toLowerCase().equals("metric")) {
      try {
        this.metrics.getId(oldname);
        this.metrics.rename(oldname, newname);
      } catch (NoSuchUniqueName nsue) {
        throw new IllegalArgumentException("Name(\"" + oldname +
            "\") does not exist");
      }
    } else if (type.toLowerCase().equals("tagk")) {
      try {
        this.tag_names.getId(oldname);
        this.tag_names.rename(oldname, newname);
      } catch (NoSuchUniqueName nsue) {
        throw new IllegalArgumentException("Name(\"" + oldname +
            "\") does not exist");
      }
    } else if (type.toLowerCase().equals("tagv")) {
      try {
        this.tag_values.getId(oldname);
        this.tag_values.rename(oldname, newname);
      } catch (NoSuchUniqueName nsue) {
        throw new IllegalArgumentException("Name(\"" + oldname +
            "\") does not exist");
      }
    } else {
      LOG.warn("Unknown type name: " + type);
      throw new IllegalArgumentException("Unknown type name");
    }
  }

  /** @return the name of the UID table as a byte array for client requests */
  public byte[] uidTable() {
    return this.uidtable;
  }
  
  /** @return the name of the data table as a byte array for client requests */
  public byte[] dataTable() {
    return this.table;
  }
  
  /** @return the name of the tree table as a byte array for client requests */
  public byte[] treeTable() {
    return this.treetable;
  }
  
  /** @return the name of the meta table as a byte array for client requests */
  public byte[] metaTable() {
    return this.meta_table;
  }

  /**
   * Index the given timeseries meta object via the configured search plugin
   * @param meta The meta data object to index
   * @since 2.0
   */
  public void indexTSMeta(final TSMeta meta) {
    if (search != null) {
      search.indexTSMeta(meta).addErrback(new PluginError());
    }
  }
  
  public void deleteTSMeta(final String tsuid) {
    if (search != null) {
      search.deleteTSMeta(tsuid).addErrback(new PluginError());
    }
  }
  
  public void indexUIDMeta(final UIDMeta meta) {
    if (search != null) {
      search.indexUIDMeta(meta).addErrback(new PluginError());
    }
  }
  
  public void deleteUIDMeta(final UIDMeta meta) {
    if (search != null) {
      search.deleteUIDMeta(meta).addErrback(new PluginError());
    }
  }
  
  public void indexAnnotation(final Annotation note) {
    if (search != null) {
      search.indexAnnotation(note).addErrback(new PluginError());
    }
    if( rt_publisher != null ) {
    	rt_publisher.publishAnnotation(note);
    }
  }
  
  public void deleteAnnotation(final Annotation note) {
    if (search != null) {
      search.deleteAnnotation(note).addErrback(new PluginError());
    }
  }
  
  public Deferred<Boolean> processTSMetaThroughTrees(final TSMeta meta) {
    if (config.enable_tree_processing()) {
      return TreeBuilder.processAllTrees(this, meta);
    }
    return Deferred.fromResult(false);
  }
  
  public Deferred<SearchQuery> executeSearch(final SearchQuery query) {
    if (search == null) {
      throw new IllegalStateException(
          "Searching has not been enabled on this TSD");
    }
    
    return search.executeQuery(query);
  }
  
  final class PluginError implements Callback<Object, Exception> {
    @Override
    public Object call(final Exception e) throws Exception {
      LOG.error("Exception from Search plugin indexer", e);
      return null;
    }
  }
  
  public void preFetchHBaseMeta() {
    LOG.info("Pre-fetching meta data for all tables");
    final long start = System.currentTimeMillis();
    final ArrayList<Deferred<Object>> deferreds = new ArrayList<Deferred<Object>>();
    deferreds.add(client.prefetchMeta(table));
    deferreds.add(client.prefetchMeta(uidtable));
    
    // TODO(cl) - meta, tree, etc
    
    try {
      Deferred.group(deferreds).join();
      LOG.info("Fetched meta data for tables in " + 
          (System.currentTimeMillis() - start) + "ms");
    } catch (InterruptedException e) {
      LOG.error("Interrupted", e);
      Thread.currentThread().interrupt();
      return;
    } catch (Exception e) {
      LOG.error("Failed to prefetch meta for our tables", e);
    }
  }
  
  /** @return the timer used for various house keeping functions */
  public Timer getTimer() {
    return timer;
  }
  
  // ------------------ //
  // Compaction helpers //
  // ------------------ //

  final KeyValue compact(final ArrayList<KeyValue> row, 
      List<Annotation> annotations) {
    return compactionq.compact(row, annotations);
  }

  final void scheduleForCompaction(final byte[] row, final int base_time) {
    if (config.enable_compactions()) {
      compactionq.add(row);
    }
  }

  // ------------------------ //
  // HBase operations helpers //
  // ------------------------ //

  /** Gets the entire given row from the data table. */
  final Deferred<ArrayList<KeyValue>> get(final byte[] key) {
    return client.get(new GetRequest(table, key));
  }

  /** Puts the given value into the data table. */
  final Deferred<Object> put(final byte[] key,
                             final byte[] qualifier,
                             final byte[] value) {
    return client.put(new PutRequest(table, key, FAMILY, qualifier, value));
  }

  /** Deletes the given cells from the data table. */
  final Deferred<Object> delete(final byte[] key, final byte[][] qualifiers) {
    return client.delete(new DeleteRequest(table, key, FAMILY, qualifiers));
  }

}
