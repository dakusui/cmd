package com.github.dakusui.cmd.ut.io;

import com.github.dakusui.cmd.core.StreamUtils;
import com.github.dakusui.cmd.utils.TestUtils;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.core.ConcurrencyUtils.updateAndNotifyAll;
import static com.github.dakusui.cmd.core.ConcurrencyUtils.waitWhile;
import static com.github.dakusui.crest.Crest.*;
import static java.util.Arrays.asList;
import static java.util.Collections.synchronizedList;
import static java.util.concurrent.Executors.newFixedThreadPool;

public class StreamUtilsTest extends TestUtils.TestBase {
  @Test(timeout = 3_000)
  public void givenOneStream$whenMerge$thenOutputIsInOrder() {
    List<String> out = new LinkedList<>();
    StreamUtils.merge(
        newFixedThreadPool(2),
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
  public void givenUnbalancedTwoStreams$whenMerge$thenOutputIsInOrder() {
    List<String> out = new LinkedList<>();
    StreamUtils.merge(
        newFixedThreadPool(2),
        10_000,
        TestUtils.dataStream("data", 100_000),
        Stream.empty())
        .peek(System.out::println)
        .forEach(out::add);

    assertThat(
        out,
        asInteger("size").eq(100000).$());
  }

  @Test
  public void givenData$whenTee$thenAllDataStreamed() {
    List<String> out = synchronizedList(new LinkedList<>());
    int numDownstreams = 2;
    AtomicInteger remaining = new AtomicInteger(numDownstreams);
    ExecutorService threadPoolForTestSide = newFixedThreadPool(numDownstreams);
    StreamUtils.<String>tee(
        newFixedThreadPool(2),
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

  @Test
  public void givenData$whenPartition$thenAllDataStreamedCorrectly() {
    List<String> out = synchronizedList(new LinkedList<>());
    int numDownstreams = 2;
    AtomicInteger remaining = new AtomicInteger(numDownstreams);
    ExecutorService threadPoolForTestSide = newFixedThreadPool(numDownstreams);
    StreamUtils.partition(
        newFixedThreadPool(2),
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

  @Test(timeout = 100_000)
  public void partitionAndThenMerge100_000() {
    TestUtils.merge(
        TestUtils.partition(TestUtils.dataStream("data", 100_000))
    ).forEach(System.out::println);
  }

  @Test(timeout = 10_000)
  public void partitionAndThenMerge100() {
    TestUtils.merge(
        TestUtils.partition(TestUtils.dataStream("data", 100))
    ).forEach(System.out::println);
  }


  @Test(timeout = 10_000)
  public void partition100() {
    TestUtils.partition(TestUtils.dataStream("data", 100))
        .forEach(s -> s.forEach(System.out::println));
  }

  @Test(timeout = 10_000)
  public void tee100() {
    TestUtils.tee(TestUtils.dataStream("data", 100))
        .forEach(s -> s.forEach(System.out::println));
  }

  @Test(timeout = 10_000)
  public void data100() {
    TestUtils.dataStream("data", 100).forEach(System.out::println);
  }

}
