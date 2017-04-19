package com.abc.datamining.modules.classifier.decisiontree.mr.dt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.abc.datamining.modules.classifier.decisiontree.data.Instance;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
import com.abc.datamining.modules.classifier.decisiontree.builder.Builder;
import com.abc.datamining.modules.classifier.decisiontree.builder.DecisionTreeC45Builder;
import com.abc.datamining.modules.classifier.decisiontree.data.Data;
import com.abc.datamining.modules.classifier.decisiontree.data.DataHandler;
import com.abc.datamining.modules.classifier.decisiontree.mr.writable.TreeNodeWritable;
import com.abc.datamining.modules.classifier.decisiontree.node.TreeNode;
import com.abc.datamining.modules.classifier.decisiontree.node.TreeNodeHelper;

public class DecisionTreeBuilderMR {
	
	private static void configureJob(Job job) {
		job.setJarByClass(DecisionTreeBuilderMR.class);
		
		job.setOutputKeyClass(LongWritable.class);
		job.setOutputValueClass(TreeNodeWritable.class);
		
		job.setMapperClass(DecisionTreeBuilderMapper.class);
		job.setNumReduceTasks(0); 
		
		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(SequenceFileOutputFormat.class);
	}

	public static void main(String[] args) {
		Configuration configuration = new Configuration();
		try {
			String[] inputArgs = new GenericOptionsParser(
						configuration, args).getRemainingArgs();
			if (inputArgs.length != 2) {
				System.out.println("error");
				System.exit(2);
			}
			Job job = Job.getInstance(configuration, "Decision Tree");
			
			FileInputFormat.setInputPaths(job, new Path(inputArgs[0]));
//			FileInputFormat.addInputPath(job, new Path(inputArgs[0]));
			FileOutputFormat.setOutputPath(job, new Path(inputArgs[1]));
			
			configureJob(job);
			
			System.out.println(job.waitForCompletion(true) ? 0 : 1);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

class DecisionTreeBuilderMapper extends Mapper<LongWritable, Text, 
	LongWritable, TreeNodeWritable> {
	
	private List<Instance> instances = new ArrayList<Instance>();
	
	private Set<String> attributes = new HashSet<String>();
	
	@Override
	protected void setup(Context context) throws IOException,
			InterruptedException {
		super.setup(context);
	}

	@Override
	protected void map(LongWritable key, Text value, Context context)
			throws IOException, InterruptedException {
		String line = value.toString();
		instances.add(DataHandler.extract(line, attributes));
	}
	
	@Override
	protected void cleanup(Context context) throws IOException, InterruptedException {
		super.cleanup(context);
		Data data = new Data(attributes.toArray(new String[0]), instances);
		DataHandler.fill(data, 0);
		System.out.println("builder map data attribute len: " + data.getAttributes().length);
		System.out.println("builder map data instances len: " + data.getInstances().size());
		Builder builder = new DecisionTreeC45Builder();
		Object result = builder.build(data);
		System.out.println("builder object: " + result);
		if (result instanceof TreeNode) {
			Set<TreeNode> treeNodes = new HashSet<TreeNode>();
			TreeNodeHelper.splitTreeNode((TreeNode) result, 25, 0, treeNodes);
			int i = 0;
			for (TreeNode treeNode : treeNodes) {
				TreeNodeWritable output = new TreeNodeWritable(treeNode);
				context.write(new LongWritable(++i), output);
			}
		}
	}
}