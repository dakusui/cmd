package com.github.dakusui.cmd.ut;

import com.github.dakusui.cmd.core.stream.Merger;
import com.github.dakusui.cmd.core.stream.Partitioner;
import com.github.dakusui.cmd.core.stream.Tee;
import com.github.dakusui.cmd.utils.ConcurrencyUtils;
import com.github.dakusui.cmd.utils.StreamUtils;
import com.github.dakusui.cmd.utils.TestUtils;
import com.github.dakusui.crest.core.Matcher;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.utils.ConcurrencyUtils.shutdownThreadPoolAndAwaitTermination;
import static com.github.dakusui.cmd.utils.TestUtils.dataStream;
import static com.github.dakusui.crest.Crest.*;
import static java.util.stream.Collectors.toList;

@RunWith(Enclosed.class)
public class ConnectorTest {
  @SuppressWarnings("unchecked")
  static void executeTeeTest(int numSplits, int numItems, Function<Integer, Function<Integer, List<Stream<String>>>> tee) {
    ExecutorService threadPool = Executors.newFixedThreadPool(numSplits);
    AtomicInteger counter = new AtomicInteger(0);
    List<List<String>> outs = Collections.synchronizedList(new LinkedList<>());
    List<Stream<String>> teedData = tee.apply(numSplits).apply(numItems);
    teedData
        .forEach(
            stream -> {
              final List<String> out = new LinkedList<>();
              outs.add(counter.getAndIncrement(), out);

              threadPool.submit(() -> {
                stream.forEach(out::add);
              });
            }
        );
    shutdownThreadPoolAndAwaitTermination(threadPool);
    @SuppressWarnings("unchecked") List<Matcher<List<List<String>>>> matchers = List.class.cast(IntStream.range(0, numSplits)
        .mapToObj(
            i -> asInteger(
                call("get", i)
                    .andThen("size").$()).equalTo(numItems).$())
        .collect(toList()));
    assertThat(
        outs,
        allOf(
            matchers.toArray(new Matcher[0])
        )
    );
  }

  public static class MergerTest {
    @Test
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
      new Merger.Builder<>(TestUtils.partition(dataStream("data", 100))).build()
          .merge().forEach(System.out::println);
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

  public static class TeeTest {
    @Test(timeout = 1_000)
    public void givenShortStream$thenTeeInto3$thenStreamedCorrectly() {
      int numSplits = 3;
      int numItems = 10;
      executeTeeTest(numSplits, numItems, tee());
    }

    @Test(timeout = 10_000)
    public void givenLongStream$thenTeeInto3$thenStreamedCorrectly() {
      int numSplits = 3;
      int numItems = 1_000_000;
      executeTeeTest(numSplits, numItems, tee());
    }

    @Test(timeout = 10_000)
    public void givenLongStream$thenTeeInto1$thenStreamedCorrectly() {
      int numSplits = 1;
      int numItems = 1_000_000;
      executeTeeTest(
          numSplits,
          numItems,
          tee()
      );
    }

    Function<Integer, Function<Integer, List<Stream<String>>>> tee() {
      return nS -> nI ->
          new Tee.Builder<>(dataStream("data", nI))
              .numQueues(nS)
              .build()
              .tee();
    }

  }

  public static  class TeeTest2 extends TeeTest {
    @Override
    Function<Integer, Function<Integer, List<Stream<String>>>> tee() {
      return nS -> nI -> StreamUtils.tee(
          Executors.newFixedThreadPool(nS),
          ConcurrencyUtils::shutdownThreadPoolAndAwaitTermination,
          dataStream("data", nI),
          nS,
          5000
      );
    }
  }
  public static class PartitionerTest {

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
