package com.github.dakusui.cmd.ut;

import com.github.dakusui.cmd.core.process.ProcessStreamer;
import com.github.dakusui.cmd.core.process.Shell;
import com.github.dakusui.cmd.core.stream.Merger;
import com.github.dakusui.cmd.core.stream.Partitioner;
import com.github.dakusui.cmd.utils.TestUtils;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.utils.TestUtils.dataStream;

@RunWith(Enclosed.class)
public class ProcessStreamerConnectionTest {
  public static class PartitioningAndMerging extends TestUtils.TestBase {
    @Test(timeout = 10_000)
    public void testPartitioning() {
      ExecutorService threadPool = Executors.newFixedThreadPool(3);
      TestUtils.partition(dataStream("A", 10_000)).stream()
          .map((Stream<String> s) ->
              ProcessStreamer.pipe(s).command("cat -n")
                  .build().stream())
          .forEach(s -> threadPool.submit(() -> s.forEach(System.out::println)));
      threadPool.shutdown();
    }

    @Test(timeout = 10_000)
    public void testPartitioner_100k() {
      ProcessStreamer ps = ProcessStreamer.pipe(dataStream("A", 100_000))
          .command("cat -n")
          .build();
      new Merger.Builder<>(
          new Partitioner.Builder<>(ps.stream()).build().partition()
      ).build().merge().forEach(System.out::println);
    }

    @Test(timeout = 5_000)
    public void testPartitioningAndMerging() {
      TestUtils.merge(
          TestUtils.partition(
              dataStream("A", 10_000)).stream()
              .map(s -> ProcessStreamer.pipe(s, Shell.local())
                  .command("cat -n")
                  .build().stream())
              .collect(Collectors.toList()))
          .forEach(System.out::println);
    }

  }
}
