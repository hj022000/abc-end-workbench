package com.abc.basic.algoritms.base.graph.shortestpath;

import com.abc.basic.algoritms.base.graph.Graph;
import com.abc.basic.algoritms.base.graph.GraphPath;
import com.abc.basic.algoritms.base.graph.GraphWalk;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class BaseShortestPathAlgorithm<V, E>
        implements ShortestPathAlgorithm<V, E>
{
    protected final Graph<V, E> graph;

    public BaseShortestPathAlgorithm(Graph<V, E> graph)
    {
        this.graph = Objects.requireNonNull(graph, "Graph is null");
    }

    @Override
    public SingleSourcePaths<V, E> getPaths(V source)
    {
        if (!graph.containsVertex(source)) {
            throw new IllegalArgumentException("graph must contain the source vertex");
        }

        Map<V, GraphPath<V, E>> paths = new HashMap<>();
        for (V v : graph.vertexSet()) {
            paths.put(v, getPath(source, v));
        }
        return new ListSingleSourcePathsImpl<>(graph, source, paths);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getPathWeight(V source, V sink)
    {
        GraphPath<V, E> p = getPath(source, sink);
        if (p == null) {
            return Double.POSITIVE_INFINITY;
        } else {
            return p.getWeight();
        }
    }

    protected final GraphPath<V, E> createEmptyPath(V source, V sink)
    {
        if (source.equals(sink)) {
            return new GraphWalk<>(
                    graph, source, sink, Collections.singletonList(source), Collections.emptyList(),
                    0d);
        } else {
            return null;
        }
    }

}
