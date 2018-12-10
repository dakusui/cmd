package com.github.dakusui.cmd.ut;

import com.github.dakusui.cmd.core.stream.Merger;
import com.github.dakusui.cmd.core.stream.Partitioner;
import com.github.dakusui.cmd.utils.StreamUtils;
import com.github.dakusui.cmd.utils.TestUtils;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.utils.TestUtils.dataStream;
import static com.github.dakusui.crest.Crest.asInteger;
import static com.github.dakusui.crest.Crest.assertThat;
import static java.util.stream.Collectors.toList;

@RunWith(Enclosed.class)
public class ConnectorTest {

  public static class MergerTest {
    @Test(timeout = 10_000)
    public void mergerTest() {
      new Merger.Builder<>(
          dataStream("A", 1_000),
          dataStream("B", 1_000),
          dataStream("C", 1_000),
          dataStream("D", 1_000),
          dataStream("E", 1_000),
          dataStream("F", 1_000),
          dataStream("G", 1_000),
          dataStream("H", 1_000)).numQueues(8).build()
          .merge()
          .forEach(System.out::println);
    }

    @Test(timeout = 10_000)
    public void tee100m() {
      new Merger.Builder<>(TestUtils.tee(dataStream("data", 100)))
          .build()
          .merge()
          .forEach(System.out::println);
    }

    @Test(timeout = 1_000)
    public void partition100() {
      new Merger.Builder<>(
          TestUtils.partition(
              dataStream("data", 100)))
          .build()
          .merge()
          .forEach(System.out::println);
    }

    @Test(timeout = 2_000)
    public void partition1000_2() {
      List<Stream<String>> streams =
          StreamUtils.partition(
              Executors.newFixedThreadPool(10),
              ExecutorService::shutdown,
              dataStream("data", 1_000),
              4,
              100,
              Object::hashCode);
      try (Stream<String> s = new Merger.Builder<>(streams).build().merge()) {
        s.forEach(System.out::println);
      }
    }
  }

  public static class PartitionerTest {
    @Test
    public void placeHolder() {
    }
  }

  public static class PartitionerAndMergerTest {
    @Test(timeout = 30_000)
    public void partitionerAndThenMerger_1M() {
      int result = new Merger.Builder<>(
          new Partitioner.Builder<>(dataStream("data", 1_000_000)).numQueues(7).build().partition()
              .stream()
              .map(s -> s.map(StreamUtilsTest.PartitionAndMerge::process))
              .collect(toList())
      ).build()
          .merge()
          .reduce((v, w) -> v + w).orElseThrow(RuntimeException::new);
      System.out.println(result);
    }

    @Test(timeout = 60_000)
    public void partitionerAndThenMerger_10M() {
      int result = new Merger.Builder<>(
          new Partitioner.Builder<>(dataStream("data", 10_000_000)).numQueues(7).build().partition()
              .stream()
              .map(s -> s.map(StreamUtilsTest.PartitionAndMerge::process))
              .collect(toList())
      ).build()
          .merge()
          .reduce((v, w) -> v + w).orElseThrow(RuntimeException::new);
      System.out.println(result);
    }

    @Test(timeout = 10_000)
    public void partitionerAndThenMerger() {
      int num = 100_000;
      AtomicInteger counter = new AtomicInteger(0);
      new Merger.Builder<>(
          new Partitioner.Builder<>(dataStream("A", num)).numQueues(4).build().partition()
              .stream()
              .map(s -> s.peek(v -> System.err.println(Thread.currentThread().getId())))
              .collect(toList())
      ).build()
          .merge()
          .forEach(t -> counter.getAndIncrement());
      assertThat(
          counter,
          asInteger("get").equalTo(num).$()
      );
    }
  }
}
