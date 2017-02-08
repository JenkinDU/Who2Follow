package who2follow;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Mapper.Context;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 * Who2Follow
 */
public class Who2Follow {

	/********************/
	/** 	Mapper     **/
	/********************/
	public static class AllPairsMapper extends Mapper<Object, Text, IntWritable, IntWritable> {
		public void map(Object key, Text values, Context context) throws IOException, InterruptedException {
			System.out.print("k:"+key+"-v:"+values);
		}
	}

	/**********************/
	/** 	Reducer 	 **/
	/**********************/
	public static class CountReducer extends Reducer<IntWritable, IntWritable, IntWritable, Text> {

	}
	
	// The reduce method
	public void reduce(IntWritable key, Iterable<IntWritable> values, Context context)
			throws IOException, InterruptedException {

	}

	public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException {
		Configuration conf = new Configuration();
		Job job = Job.getInstance(conf, "who to follow");
		job.setJarByClass(Who2Follow.class);
		job.setMapperClass(AllPairsMapper.class);
		job.setReducerClass(CountReducer.class);
		job.setOutputKeyClass(IntWritable.class);
		job.setOutputValueClass(IntWritable.class);
		FileInputFormat.addInputPath(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}