//package who2follow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.StringTokenizer;
import java.util.function.Predicate;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.jobcontrol.ControlledJob;
import org.apache.hadoop.mapreduce.lib.jobcontrol.JobControl;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 * Who2Follow
 */
public class Who2Follow {
	/**
	 * Revert the key value
	 * 
	 * @author Zhen Du
	 *
	 */
	public static class RevertMapper extends Mapper<Object, Text, IntWritable, IntWritable> {

		public void map(Object key, Text values, Context context) throws IOException, InterruptedException {
			StringTokenizer st = new StringTokenizer(values.toString());
			// Text user = new Text(st.nextToken());
			IntWritable user = new IntWritable(Integer.parseInt(st.nextToken()));
			while (st.hasMoreTokens()) {
				IntWritable i = new IntWritable(Integer.parseInt(st.nextToken()));
				// System.out.println("k:"+key+"v:"+i.get());
				context.write(i, user);
				context.write(user, new IntWritable(-i.get()));
			}
		}
	}

	/**
	 * Reduce the revert paired values
	 * 
	 * @author Zhen Du
	 *
	 */
	public static class RevertReducer extends Reducer<IntWritable, IntWritable, IntWritable, Text> {
		// The reduce method
		public void reduce(IntWritable key, Iterable<IntWritable> values, Context context)
				throws IOException, InterruptedException {
			String value = "";
			while (values.iterator().hasNext()) {
				int i = values.iterator().next().get();
				value += (i + " ");
			}
			context.write(key, new Text(value));
		}

	}

	/**
	 * Emit the paired values
	 * 
	 * @author Zhen Du
	 *
	 */
	public static class AllPairsMapper extends Mapper<Object, Text, IntWritable, IntWritable> {

		public void map(Object key, Text values, Context context) throws IOException, InterruptedException {
			StringTokenizer st = new StringTokenizer(values.toString());
			IntWritable user = new IntWritable(Integer.parseInt(st.nextToken()));
			// 'friends' will store the list of friends of user 'user'
			ArrayList<Integer> friends = new ArrayList<>();
			// First, go through the list of all friends of user 'user' and emit
			// (user,-friend)
			// 'friend1' will be used in the emitted pair
			IntWritable friend1 = new IntWritable();
			while (st.hasMoreTokens()) {
				Integer friend = Integer.parseInt(st.nextToken());
				if (friend.intValue() < 0) {
					friend1.set(friend);
					context.write(user, friend1);
				} else {
					friends.add(friend);
				}
			}
			// Now we can emit all (a,b) and (b,a) pairs
			// where a!=b and a & b are friends of user 'user'.
			// We use the same algorithm as before.
			ArrayList<Integer> seenFriends = new ArrayList<>();
			// The element in the pairs that will be emitted.
			IntWritable friend2 = new IntWritable();
			for (Integer friend : friends) {
				friend1.set(friend);
				for (Integer seenFriend : seenFriends) {
					friend2.set(seenFriend);
					context.write(friend1, friend2);
					context.write(friend2, friend1);
				}
				seenFriends.add(friend1.get());
			}
		}
	}

	/**
	 * Reduce the paired values
	 * 
	 * @author Zhen Du
	 *
	 */
	public static class CountReducer extends Reducer<IntWritable, IntWritable, IntWritable, Text> {
		// A private class to describe a recommendation.
		// A recommendation has a friend id and a number of friends in common.
		private static class Recommendation {

			// Attributes
			private int friendId;
			private int nCommonFriends;

			// Constructor
			public Recommendation(int friendId) {
				this.friendId = friendId;
				// A recommendation must have at least 1 common friend
				this.nCommonFriends = 1;
			}

			// Getters
			public int getFriendId() {
				return friendId;
			}

			public int getNCommonFriends() {
				return nCommonFriends;
			}

			// Other methods
			// Increments the number of common friends
			public void addCommonFriend() {
				nCommonFriends++;
			}

			// String representation used in the reduce output
			public String toString() {
				return friendId + "(" + nCommonFriends + ")";
			}

			// Finds a representation in an array
			public static Recommendation find(int friendId, ArrayList<Recommendation> recommendations) {
				for (Recommendation p : recommendations) {
					if (p.getFriendId() == friendId) {
						return p;
					}
				}
				// Recommendation was not found!
				return null;
			}
		}

		// The reduce method
		public void reduce(IntWritable key, Iterable<IntWritable> values, Context context)
				throws IOException, InterruptedException {
			IntWritable user = key;
			// 'existingFriends' will store the friends of user 'user'
			// (the negative values in 'values').
			ArrayList<Integer> existingFriends = new ArrayList<Integer>();
			// 'recommendedUsers' will store the list of user ids recommended
			// to user 'user'
			ArrayList<Integer> recommendedUsers = new ArrayList<Integer>();
			while (values.iterator().hasNext()) {
				int value = values.iterator().next().get();
				if (value > 0) {
					recommendedUsers.add(value);
				} else {
					existingFriends.add(value);
				}
			}
			// 'recommendedUsers' now contains all the positive values in
			// 'values'.
			// We need to remove from it every value -x where x is in
			// existingFriends.
			// See javadoc on Predicate:
			// https://docs.oracle.com/javase/8/docs/api/java/util/function/Predicate.html
			for (Integer friend : existingFriends) {
				recommendedUsers.removeIf(new Predicate<Integer>() {
					@Override
					public boolean test(Integer t) {
						return t.intValue() == -friend.intValue();
					}
				});
			}
			ArrayList<Recommendation> recommendations = new ArrayList<>();
			// Builds the recommendation array
			for (Integer userId : recommendedUsers) {
				Recommendation p = Recommendation.find(userId, recommendations);
				if (p == null) {
					recommendations.add(new Recommendation(userId));
				} else {
					p.addCommonFriend();
				}
			}
			// Sorts the recommendation array
			// See javadoc on Comparator at
			// https://docs.oracle.com/javase/8/docs/api/java/util/Comparator.html
			recommendations.sort(new Comparator<Recommendation>() {
				@Override
				public int compare(Recommendation t, Recommendation t1) {
					return -Integer.compare(t.getNCommonFriends(), t1.getNCommonFriends());
				}
			});
			// Builds the output string that will be emitted
			StringBuffer sb = new StringBuffer(""); // Using a StringBuffer is
													// more efficient than
													// concatenating strings
			for (int i = 0; i < recommendations.size() && i < 10; i++) {
				Recommendation p = recommendations.get(i);
				sb.append(p.toString() + " ");
			}
			Text result = new Text(sb.toString());
			context.write(user, result);
		}
	}

	public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException {
		Configuration conf = new Configuration();
		Job job = Job.getInstance(conf, "who to follow1");
		job.setJarByClass(Who2Follow.class);
		job.setMapperClass(RevertMapper.class);
		job.setReducerClass(RevertReducer.class);
		job.setOutputKeyClass(IntWritable.class);
		job.setOutputValueClass(IntWritable.class);
		FileInputFormat.addInputPath(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));
		ControlledJob ctrljob1 = new ControlledJob(conf);
		ctrljob1.setJob(job);
		// job2
		Job job1 = Job.getInstance(conf, "who to follow2");
		job1.setMapperClass(AllPairsMapper.class);
		job1.setReducerClass(CountReducer.class);
		job1.setOutputKeyClass(IntWritable.class);
		job1.setOutputValueClass(IntWritable.class);
		FileInputFormat.addInputPath(job1, new Path(args[1]));
		FileOutputFormat.setOutputPath(job1, new Path(args[2]));
		ControlledJob ctrljob2 = new ControlledJob(conf);
		ctrljob2.setJob(job1);

		ctrljob2.addDependingJob(ctrljob1);
		JobControl jc = new JobControl("who to follow");
		jc.addJob(ctrljob1);
		jc.addJob(ctrljob2);

		Thread t = new Thread(jc);
		t.start();
		while (true) {
			if (jc.allFinished()) {
				System.out.println(jc.getSuccessfulJobList());
				jc.stop();
				break;
			}
		}
		// System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}