package com.github.dakusui.cmd.ut;

import com.github.dakusui.cmd.core.stream.Merger;
import com.github.dakusui.cmd.core.stream.Partitioner;
import com.github.dakusui.cmd.utils.ConcurrencyUtils;
import com.github.dakusui.cmd.utils.StreamUtils;
import com.github.dakusui.cmd.utils.TestUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.utils.ConcurrencyUtils.updateAndNotifyAll;
import static com.github.dakusui.cmd.utils.ConcurrencyUtils.waitWhile;
import static com.github.dakusui.cmd.utils.TestUtils.dataStream;
import static com.github.dakusui.crest.Crest.*;
import static com.github.dakusui.crest.utils.printable.Predicates.matchesRegex;
import static java.util.Arrays.asList;
import static java.util.Collections.synchronizedList;
import static java.util.concurrent.Executors.newFixedThreadPool;

@RunWith(Enclosed.class)
public class StreamUtilsTest extends TestUtils.TestBase {
  public static class MergingTest extends TestUtils.TestBase {
    @Test(timeout = 3_000)
    public void givenOneStream$whenMerge$thenOutputIsInOrder() {
      List<String> out = new LinkedList<>();
      StreamUtils.merge(
          newFixedThreadPool(2),
          ConcurrencyUtils::shutdownThreadPoolAndAwaitTermination,
          1,
          Stream.of("A", "B", "C", "D", "E", "F", "G", "H"))
          .peek(System.out::println)
          .forEach(out::add);

      assertThat(
          out,
          allOf(
              asListOf(String.class, sublistAfterElement("A").afterElement("H").$())
                  .isEmpty().$(),
              asInteger("size").eq(8).$()));
    }

    @Test(timeout = 3_000)
    public void givenTwoStreams$whenMerge$thenOutputIsInOrder() {
      List<String> out = new LinkedList<>();
      StreamUtils.merge(
          newFixedThreadPool(2),
          ConcurrencyUtils::shutdownThreadPoolAndAwaitTermination,
          1,
          Stream.of("A", "B", "C", "D", "E", "F", "G", "H"),
          Stream.of("a", "b", "c", "d", "e", "f", "g", "h"))
          .peek(System.out::println)
          .forEach(out::add);

      assertThat(
          out,
          allOf(
              anyOf(
                  asListOf(String.class, sublistAfterElement("A").afterElement("H").$()).isEmpty().$(),
                  asListOf(String.class, sublistAfterElement("a").afterElement("h").$()).isEmpty().$()),
              asInteger("size").eq(16).$()));
    }

    @Test(timeout = 10_000)
    public void givenTwoMediumSizeStreams$whenMerge$thenOutputIsInOrder() {
      int sizeOfEachStream = 100_000;
      List<String> out = new LinkedList<>();
      StreamUtils.merge(
          newFixedThreadPool(2),
          ExecutorService::shutdown,
          1,
          dataStream("A", sizeOfEachStream),
          dataStream("B", sizeOfEachStream))
          .peek(System.out::println)
          .forEach(out::add);

      assertThat(
          out,
          allOf(
              asListOf(String.class).allMatch(matchesRegex("[AB]-[0-9]+")).$(),
              asInteger("size").eq(sizeOfEachStream * 2).$()));
    }

    @Test(timeout = 30_000)
    public void givenTwoLargeSizeStreams$whenMerge$thenOutputIsInOrder() {
      int sizeOfEachStream = 1_000_000;
      List<String> out = new LinkedList<>();
      StreamUtils.merge(
          newFixedThreadPool(2),
          ConcurrencyUtils::shutdownThreadPoolAndAwaitTermination,
          1,
          dataStream("A", sizeOfEachStream),
          dataStream("B", sizeOfEachStream))
          .peek(System.out::println)
          .forEach(out::add);

      assertThat(
          out,
          allOf(
              asListOf(String.class).allMatch(matchesRegex("[AB]-[0-9]+")).$(),
              asInteger("size").eq(sizeOfEachStream * 2).$()));
    }

    @Test(timeout = 10_000)
    public void givenUnbalancedTwoStreams$whenMerge$thenOutputIsInOrder() {
      List<String> out = new LinkedList<>();
      StreamUtils.merge(
          newFixedThreadPool(2),
          ConcurrencyUtils::shutdownThreadPoolAndAwaitTermination,
          10_000,
          dataStream("data", 100_000),
          Stream.empty())
          .peek(System.out::println)
          .forEach(out::add);

      assertThat(
          out,
          asInteger("size").eq(100000).$());
    }
  }

  public static class TeeTest extends TestUtils.TestBase {
    @Test(timeout = 10_000)
    public void givenTenElements$whenTee$thenAllStreamed() {
      int num = 10;
      Set<String> resultSet = Collections.synchronizedSet(new HashSet<>());
      TestUtils.tee(dataStream("data", num))
          .forEach(
              s -> s.peek(resultSet::add)
                  .forEach(System.out::println));

      assertThat(
          resultSet,
          asInteger("size").equalTo(num).$()
      );
    }

    @Test(timeout = 10_000)
    public void given100Elements$whenTee$thenAllStreamed() {
      int num = 100;
      Set<String> resultSet = Collections.synchronizedSet(new HashSet<>());
      TestUtils.tee(dataStream("data", num))
          .forEach(
              s -> s.peek(resultSet::add)
                  .forEach(System.out::println));

      assertThat(
          resultSet,
          asInteger("size").equalTo(num).$()
      );
    }

    @Test
    public void givenData$whenTee$thenAllDataStreamed() {
      List<String> out = synchronizedList(new LinkedList<>());
      int numDownstreams = 2;
      AtomicInteger remaining = new AtomicInteger(numDownstreams);
      ExecutorService threadPoolForTestSide = newFixedThreadPool(numDownstreams);
      StreamUtils.tee(
          newFixedThreadPool(2),
          ConcurrencyUtils::shutdownThreadPoolAndAwaitTermination,
          Stream.of("A", "B", "C", "D", "E", "F", "G", "H"), numDownstreams, 1)
          .forEach(
              s -> threadPoolForTestSide.submit(
                  () -> {
                    s.peek(out::add).forEach(
                        x -> System.out.println(Thread.currentThread().getId() + ":" + x)
                    );
                    synchronized (remaining) {
                      updateAndNotifyAll(remaining, AtomicInteger::decrementAndGet);
                    }
                  }));
      synchronized (remaining) {
        waitWhile(remaining, c -> c.get() > 0);
      }
      assertThat(
          out,
          allOf(
              asInteger("size").equalTo(8 * 2).$(),
              asListOf(String.class,
                  sublistAfterElement("A")
                      .afterElement("B")
                      .afterElement("C")
                      .afterElement("D")
                      .afterElement("E")
                      .afterElement("F")
                      .afterElement("G")
                      .afterElement("H")
                      .$()).$(),
              asListOf(String.class,
                  sublistAfterElement("A").afterElement("A").$()).$(),
              asListOf(String.class,
                  sublistAfterElement("H").afterElement("H").$()).$()
          )
      );
    }
  }

  public static class PartitioningTest extends TestUtils.TestBase {
    @Test(timeout = 1_000)
    public void partition100parallelWithTestUtils() {
      TestUtils.partition(dataStream("data", 100))
          .stream()
          .parallel()
          .forEach(s -> s.forEach(System.out::println));
    }

    @Test(timeout = 5_000)
    public void partition1000parallelWithTestUtils() {
      TestUtils.partition(dataStream("data", 1_000))
          .stream()
          .parallel()
          .forEach(s -> s.forEach(System.out::println));
    }

    @Test(timeout = 1_000)
    public void partition10testUtils() {
      TestUtils.partition(dataStream("data", 10))
          .forEach(s -> s.forEach(System.out::println));
    }

    @Test(timeout = 1_000)
    public void partition100testUtils() {
      TestUtils.partition(dataStream("data", 100))
          .forEach(s -> s.forEach(System.out::println));
    }

    @Test(timeout = 1_000)
    public void partition1000testUtils() {
      TestUtils.partition(dataStream("data", 1000))
          .forEach(s -> s.forEach(System.out::println));
    }

    @Test(timeout = 2_000)
    public void partition1000testUtilsAndThenMZerger() {
      List<Stream<String>> streams = TestUtils.partition(dataStream("data", 1_000));
      try (Stream<String> s = new Merger.Builder<>(streams).build().merge()) {
        s.forEach(System.out::println);
      }
    }

    @Test(timeout = 10_000)
    public void partition100_000testUtilsAndThehMerger() {
      new Merger.Builder<>(TestUtils.partition(dataStream("data", 100_000))).build()
          .merge().forEach(System.out::println);
    }

    @Test(timeout = 3_000)
    public void givenData$whenPartition$thenAllDataStreamedCorrectly() {
      List<String> out = synchronizedList(new LinkedList<>());
      int numDownstreams = 2;
      AtomicInteger remaining = new AtomicInteger(numDownstreams);
      ExecutorService threadPoolForTestSide = newFixedThreadPool(numDownstreams + 1);
      StreamUtils.partition(
          newFixedThreadPool(2),
          ConcurrencyUtils::shutdownThreadPoolAndAwaitTermination,
          Stream.of("A", "B", "C", "D", "E", "F", "G", "H"), numDownstreams, 100, String::hashCode)
          .forEach(
              s -> threadPoolForTestSide.submit(
                  () -> {
                    s.peek(out::add).forEach(
                        x -> System.out.println(Thread.currentThread().getId() + ":" + x)
                    );
                    synchronized (remaining) {
                      updateAndNotifyAll(remaining, AtomicInteger::decrementAndGet);
                    }
                  }));
      synchronized (remaining) {
        waitWhile(remaining, c -> c.get() > 0);
      }
      assertThat(
          out,
          allOf(
              asInteger("size").equalTo(8).$(),
              asListOf(String.class).containsExactly(asList("A", "B", "C", "D", "E", "F", "G", "H")).$()
          )
      );
    }

    @Test
    public void given10000Data$whenPartition$thenAllDataStreamedCorrectly() {
      List<String> out = synchronizedList(new LinkedList<>());
      int numDownstreams = 6;
      int dataSize = 10_000;
      AtomicInteger remaining = new AtomicInteger(numDownstreams);
      ExecutorService threadPoolForTestSide = newFixedThreadPool(numDownstreams);
      StreamUtils.partition(
          newFixedThreadPool(3),
          ConcurrencyUtils::shutdownThreadPoolAndAwaitTermination,
          dataStream("A", dataSize), numDownstreams, 1, String::hashCode)
          .forEach(
              s -> threadPoolForTestSide.submit(
                  () -> {
                    s.peek(out::add)
                        .forEach(x -> System.out.println(Thread.currentThread().getId() + ":" + x));
                    synchronized (remaining) {
                      updateAndNotifyAll(remaining, AtomicInteger::decrementAndGet);
                    }
                  }));
      synchronized (remaining) {
        waitWhile(remaining, c -> c.get() > 0);
      }
      assertThat(
          out,
          allOf(
              asInteger("size").equalTo(dataSize).$(),
              asListOf(String.class).allMatch(e -> e.startsWith("A-")).$()
          )
      );
    }

    @Test(timeout = 1_000)
    public void testPartitioner() {
      int num = 1_000;
      AtomicInteger counter = new AtomicInteger(0);
      new Partitioner.Builder<>(dataStream("A", num))
          .build().forEach(t -> counter.getAndIncrement());
      assertThat(
          counter,
          asInteger("get").equalTo(num).$()
      );
    }
  }

  public static class PartitionAndMerge extends TestUtils.TestBase {

    @Test(timeout = 10_000)
    public void partitionAndThenMerge100_000() {
      int num = 1_000_000;
      AtomicInteger counter = new AtomicInteger(0);
      TestUtils.merge(
          TestUtils.partition(dataStream("data", num))
      ).forEach(t -> counter.getAndIncrement());
      assertThat(
          counter,
          asInteger("get").equalTo(num).$()
      );
    }

    @Ignore
    @Test(timeout = 20_000)
    public void plain10M() {
      // 10M 118691370 6s960ms
      System.out.println(dataStream("data", 10_000_000)
          .map(PartitionAndMerge::process)
          .reduce((v, w) -> v + w));
    }

    @Ignore
    @Test(timeout = 5_000)
    public void plain1M() {
      // 1M 10888890 1s126ms
      System.out.println(dataStream("data", 1_000_000)
          .map(PartitionAndMerge::process)
          .reduce((v, w) -> v + w));
    }

    static int process(String s) {
      return s.length();
    }
  }
}