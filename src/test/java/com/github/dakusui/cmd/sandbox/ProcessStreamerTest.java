package com.github.dakusui.cmd.sandbox;

import com.github.dakusui.cmd.Shell;
import com.github.dakusui.cmd.core.ProcessStreamer;
import com.github.dakusui.cmd.core.StreamUtils;
import com.github.dakusui.cmd.utils.TestUtils;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.github.dakusui.crest.Crest.allOf;
import static com.github.dakusui.crest.Crest.asInteger;
import static com.github.dakusui.crest.Crest.asListOf;
import static com.github.dakusui.crest.Crest.asString;
import static com.github.dakusui.crest.Crest.assertThat;
import static com.github.dakusui.crest.Crest.call;
import static com.github.dakusui.crest.Crest.sublistAfter;
import static com.github.dakusui.crest.Crest.sublistAfterElement;
import static com.github.dakusui.crest.utils.printable.Predicates.containsString;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ProcessStreamerTest extends TestUtils.TestBase {
  @Test(timeout = 1_000)
  public void givenCat$whenDrainDataAndClose$thenOutputIsCorrectAndInOrder() throws InterruptedException {
    assertThat(
        runProcessStreamer(
            () -> new ProcessStreamer.Builder(Shell.local(), "cat -n").build(),
            ps -> ps.drain(Stream.of("a", "b", "c"))),
        asListOf(String.class,
            sublistAfter(containsString("a"))
                .after(containsString("b"))
                .after(containsString("c")).$())
            .isEmpty().$());
  }

  @Test(timeout = 10_000)
  public void givenCat$whenDrainMediumSizeDataAndClose$thenOutputIsCorrectAndInOrder() throws InterruptedException {
    assertThat(
        runProcessStreamer(
            () -> new ProcessStreamer.Builder(Shell.local(), "cat -n").build(),
            ps -> ps.drain(data("data", 10_000))),
        asListOf(String.class,
            sublistAfter(containsString("a"))
                .after(containsString("b"))
                .after(containsString("c")).$())
            .isEmpty().$());
  }

  @Test(timeout = 1_000)
  public void givenCatWithMinimumQueueAndRingBufferSize$whenDrainDataAndClose$thenOutputIsCorrectAndInOrder() throws InterruptedException {
    assertThat(
        runProcessStreamer(
            () -> new ProcessStreamer.Builder(Shell.local(), "cat -n")
                .configureStderr(true, true, true)
                .queueSize(1)
                .ringBufferSize(1)
                .build(),
            ps -> ps.drain(Stream.of("a", "b", "c", "d", "e", "f", "g", "h"))),
        asListOf(String.class,
            sublistAfter(containsString("a"))
                .after(containsString("b"))
                .after(containsString("c"))
                .after(containsString("h")).$())
            .isEmpty().$());
  }

  @Test(timeout = 1_000)
  public void givenEchos$whenStream$thenOutputIsCorrectAndInOrder() throws InterruptedException {
    assertThat(
        runProcessStreamer(
            () -> new ProcessStreamer.Builder(Shell.local(), "echo hello world && echo !").build(),
            ps -> {
              ps.drain(Stream.of("a", "b", "c"));
            }),
        asListOf(String.class,
            sublistAfterElement("hello world").afterElement("!").$()).$());
  }

  @Test
  public void givenCommandResultingInError$whenExecuted$thenOutputIsCorrect() {
    class Result {
      ProcessStreamer ps = new ProcessStreamer.Builder(
          Shell.local(),
          "echo hello world && _Echo hello!").build();
      private int          exitCode;
      private List<String> out = new LinkedList<>();

      /*
       * This method is reflectively called.
       */
      @SuppressWarnings("unused")
      public int exitCode() {
        return this.exitCode;
      }

      /*
       * This method is reflectively called.
       */
      @SuppressWarnings("unused")
      public List<String> out() {
        return this.out;
      }

      /*
       * This method is reflectively called.
       */
      @SuppressWarnings("unused")
      public ProcessStreamer processStreamer() {
        return this.ps;
      }
    }
    Result result = new Result();
    result.ps.stream().peek(System.out::println).forEach(result.out::add);
    result.exitCode = result.ps.exitValue();

    System.out.println(result.ps.getPid() + "=" + result.exitCode);
    result.out.forEach(System.out::println);

    assertThat(
        result,
        allOf(
            asListOf(String.class, call("out").$())
                .anyMatch(containsString("_Echo"))
                .anyMatch(containsString("not found"))
                .anyMatch(containsString("hello world")).$(),
            asInteger("exitCode").eq(127).$(),
            asString(call("processStreamer").andThen("toString").$())
                .containsString("hello world")
                .containsString("not found").$()
        )
    );
  }

  @Test
  public void testPartitioning() {
    partition(
        data("A", 10_000)).stream()
        .map(s -> {
          ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), "cat -n").build();
          ps.drain(s);
          return ps.stream();
        })
        .forEach(s -> s.forEach(System.out::println));
  }

  @Test
  public void testPartitioningAndMerging() {
    merge(
        partition(
            data("A", 10_000)).stream()
            .map(s -> {
              ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), "cat -n").build();
              ps.drain(s);
              return ps.stream();
            })
            .collect(Collectors.toList()))
        .forEach(System.out::println);
  }

  private static Stream<String> merge(List<Stream> streams) {
    return StreamUtils.merge(newFixedThreadPool(8), 100, streams.toArray(new Stream[0]));
  }

  private static List<Stream<String>> partition(Stream<String> in) {
    return StreamUtils.partition(
        newFixedThreadPool(8),
        in,
        4,
        100,
        String::hashCode);
  }

  private static Stream<String> data(String prefix, int num) {
    return IntStream.range(0, num).mapToObj(i -> String.format("%s-%s", prefix, i));
  }

  private List<String> runProcessStreamer(Supplier<ProcessStreamer> processStreamerSupplier, Consumer<ProcessStreamer> dataDrainer) throws InterruptedException {
    ProcessStreamer ps = processStreamerSupplier.get();
    dataDrainer.accept(ps);
    List<String> out = new LinkedList<>();
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    executorService.submit(() -> ps.stream().forEach(out::add));
    System.out.println(ps.getPid() + "=" + ps.waitFor());
    executorService.shutdown();
    while (!executorService.isTerminated()) {
      executorService.awaitTermination(1, MILLISECONDS);
    }
    return out;
  }
}
