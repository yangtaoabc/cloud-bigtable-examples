package com.google.anviltop.sample;

import java.io.IOException;
import java.util.Collections;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableReducer;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

/**
 * Writes the same results of WordCount to a new table
 *
 * @author sduskis
 */
public class WordCountHBase {

  final static Log LOG = LogFactory.getLog(WordCountHBase.class);

  public static final byte[] COLUMN_FAMILY = "cf".getBytes();
  public static final byte[] COUNT_COLUMN_NAME = "count".getBytes();

  public static class TokenizerMapper extends
      Mapper<Object, Text, ImmutableBytesWritable, IntWritable> {

    private final static IntWritable one = new IntWritable(1);
    
    public TokenizerMapper() {
      LOG.info("Creating a " + getClass().getName());
    }

    @Override
    public void map(Object key, Text value, Context context) throws IOException,
        InterruptedException {
      LOG.info("mapping");
      StringTokenizer itr = new StringTokenizer(value.toString());
      ImmutableBytesWritable word = new ImmutableBytesWritable();
      while (itr.hasMoreTokens()) {
        word.set(Bytes.toBytes(itr.nextToken()));
        context.write(word, one);
      }
    }
  }

  public static class MyTableReducer extends
      TableReducer<ImmutableBytesWritable, IntWritable, ImmutableBytesWritable> {

    public MyTableReducer() {
      LOG.info("Creating a " + getClass().getName());
    }

    @Override
    public void reduce(ImmutableBytesWritable key, Iterable<IntWritable> values, Context context)
        throws IOException, InterruptedException {
      LOG.info("reducing");
      int sum = sum(values);
      Put put = new Put(key.get());
      put.add(COLUMN_FAMILY, COUNT_COLUMN_NAME, Bytes.toBytes(sum));
      context.write(null, put);
    }

    public int sum(Iterable<IntWritable> values) {
      int i = 0;
      for (IntWritable val : values) {
        i += val.get();
      }
      return i;
    }
  }

  public static void main(String[] args) throws Exception {
    Configuration conf = HBaseConfiguration.create();
    String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
    for (String arg : otherArgs) {
      System.out.println(arg);
    }
    if (otherArgs.length < 2) {
      System.err.println("Usage: wordcount-hbase <in> [<in>...] <table-name>");
      System.exit(2);
    }

    Job job = Job.getInstance(conf, "word count");

    for (int i = 0; i < otherArgs.length - 1; ++i) {
      FileInputFormat.addInputPath(job, new Path(otherArgs[i]));
    }

    TableName tableName = TableName.valueOf(otherArgs[otherArgs.length - 1]);
    try {
      CreateTable.createTable(tableName, conf,
          Collections.singletonList(Bytes.toString(COLUMN_FAMILY)));
    } catch (Exception e) {
      LOG.error("Could not create the table.", e);
    }

    job.setJarByClass(WordCountHBase.class);
    job.setMapperClass(TokenizerMapper.class);
    job.setMapOutputValueClass(IntWritable.class);

    TableMapReduceUtil.initTableReducerJob(tableName.getNameAsString(), MyTableReducer.class, job);

    System.exit(job.waitForCompletion(true) ? 0 : 1);
  }
}
