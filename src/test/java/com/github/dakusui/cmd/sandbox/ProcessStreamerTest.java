package com.github.dakusui.cmd.sandbox;

import com.github.dakusui.cmd.Shell;
import com.github.dakusui.cmd.core.Merger;
import com.github.dakusui.cmd.core.Partitioner;
import com.github.dakusui.cmd.core.ProcessStreamer;
import com.github.dakusui.cmd.utils.TestUtils;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.utils.TestUtils.dataStream;
import static com.github.dakusui.crest.Crest.allOf;
import static com.github.dakusui.crest.Crest.asInteger;
import static com.github.dakusui.crest.Crest.asListOf;
import static com.github.dakusui.crest.Crest.asString;
import static com.github.dakusui.crest.Crest.assertThat;
import static com.github.dakusui.crest.Crest.call;
import static com.github.dakusui.crest.Crest.sublistAfter;
import static com.github.dakusui.crest.Crest.sublistAfterElement;
import static com.github.dakusui.crest.utils.printable.Predicates.containsString;

@RunWith(Enclosed.class)
public class ProcessStreamerTest extends TestUtils.TestBase {
  public static class SinkTest extends TestUtils.TestBase {
    @Test(timeout = 3_000)
    public void testSink() throws IOException {
      ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), String.format("cat -n > %s", File.createTempFile("processstreamer-", "tmp")))
          .stdin(dataStream("data-", 10_000))
          .configureStdout(false, false, false)
          .build();
      ps.stream().forEach(System.out::println);
    }

    @Test(timeout = 3_000)
    public void testSinkBigger() throws IOException {
      ProcessStreamer ps = new ProcessStreamer.Builder(
          Shell.local(), String.format("cat -n > %s", File.createTempFile("processstreamer-", "tmp")))
          .stdin(dataStream("data-", 100_000))
          .configureStdout(false, false, false)
          .build();
      ps.stream().forEach(System.out::println);
    }
  }

  public static class SourceTest extends TestUtils.TestBase {
    @Test(timeout = 20_000)
    public void testSource() {
      ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(),
          "for i in $(seq 1 1000); do seq 1 10000 | paste -s -d ' ' - ; done")
          .stdin(Stream.empty())
          .configureStdout(true, true, true)
          .build();
      ps.stream().forEach(System.out::println);
    }

    @Test(timeout = 3_000)
    public void testSourceManyLines() {
      ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), "seq 1 100000")
          .configureStdout(true, true, true)
          .stdin(Stream.empty())
          .build();
      ps.stream().forEach(System.out::println);
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

    @Test(timeout = 1_000)
    public void givenEchos$whenStream$thenOutputIsCorrectAndInOrder() throws InterruptedException {
      assertThat(
          runProcessStreamer(
              () -> new ProcessStreamer.Builder(Shell.local(), "echo hello world && echo !")
                  .stdin(Stream.of("a", "b", "c"))
                  .build()),
          asListOf(String.class,
              sublistAfterElement("hello world").afterElement("!").$()).$());
    }

    @Test(timeout = 1_000, expected = ProcessStreamer.Failure.class)
    public void givenUnknownCommand$whenStream$thenFailureThrown() throws InterruptedException {
      runProcessStreamer(
          () -> new ProcessStreamer.Builder(Shell.local(), "echo___ hello world && echo !")
              .stdin(Stream.of("a", "b", "c"))
              .build());
    }
  }

  /**
   * Pipe test
   */
  public static class PipeTest extends TestUtils.TestBase {
    @Test(timeout = 1_000)
    public void givenSort$whenDrainDataAndClose$thenOutputIsCorrectAndInOrder() throws InterruptedException {
      assertThat(
          runProcessStreamer(
              () -> new ProcessStreamer.Builder(Shell.local(), "sort").stdin(Stream.of("c", "b", "a")).build()),
          asListOf(String.class,
              sublistAfter(containsString("a"))
                  .after(containsString("b"))
                  .after(containsString("c")).$())
              .isEmpty().$());
    }

    @Test(timeout = 1_000)
    public void givenSort$whenDrain1kDataAndClose$thenOutputIsCorrectAndInOrder() throws InterruptedException {
      assertThat(
          runProcessStreamer(
              () -> new ProcessStreamer.Builder(Shell.local(), "sort").stdin(dataStream("data", 1_000)).build()),
          asListOf(String.class,
              sublistAfter(containsString("997"))
                  .after(containsString("998"))
                  .after(containsString("999")).$())
              .isEmpty().$());
    }

    @Test(timeout = 60_000)
    public void givenSortPipedToCatN$whenDrain10kDataAndClose$thenOutputIsCorrectAndInOrder() throws InterruptedException {
      int num = 1_000_000;
      assertThat(
          runProcessStreamer(
              () -> new ProcessStreamer.Builder(Shell.local(), "sort").stdin(dataStream("data", num)).build()),
          asInteger("size").equalTo(num).$());
    }

    @Test(timeout = 1_000)
    public void givenCat$whenDrainDataAndClose$thenOutputIsCorrectAndInOrder() throws InterruptedException {
      assertThat(
          runProcessStreamer(
              () -> new ProcessStreamer.Builder(Shell.local(), "cat -n").stdin(Stream.of("a", "b", "c")).build()),
          asListOf(String.class,
              sublistAfter(containsString("a"))
                  .after(containsString("b"))
                  .after(containsString("c")).$())
              .isEmpty().$());
    }

    @Test(timeout = 10_000)
    public void givenCat$whenDrainMediumSizeDataAndClose$thenOutputIsCorrectAndInOrder() throws InterruptedException {
      int lines = 100_000;
      assertThat(
          runProcessStreamer(
              () -> new ProcessStreamer.Builder(Shell.local(), "cat -n")
                  .stdin(dataStream("data", lines))
                  .configureStdout(true, true, true).build()),
          allOf(
              asInteger("size").equalTo(lines).$(),
              asListOf(String.class,
                  sublistAfter(containsString("data-0"))
                      .after(containsString("data-" + (lines - 2)))
                      .after(containsString("data-" + (lines - 1))).$())
                  .isEmpty().$()
          )
      );
    }

    @Test(timeout = 1_000)
    public void givenCatWithMinimumQueueAndRingBufferSize$whenDrainDataAndClose$thenOutputIsCorrectAndInOrder() throws InterruptedException {
      assertThat(
          runProcessStreamer(
              () -> new ProcessStreamer.Builder(Shell.local(), "cat -n")
                  .stdin(Stream.of("a", "b", "c", "d", "e", "f", "g", "h"))
                  .configureStderr(true, true, true)
                  .queueSize(1)
                  .ringBufferSize(1)
                  .build()),
          asListOf(String.class,
              sublistAfter(containsString("a"))
                  .after(containsString("b"))
                  .after(containsString("c"))
                  .after(containsString("h")).$())
              .isEmpty().$());
    }

    @Test(timeout = 1_000)
    public void pipeTest() {
      ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), "cat -n")
          .stdin(dataStream("A", 10_000))
          .build();
      Executors.newSingleThreadExecutor().submit(() -> {
      });
      ps.stream().forEach(System.out::println);
    }

    @Test(timeout = 10_000)
    public void pipeTest100_000() {
      ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), "cat -n")
          .stdin(dataStream("A", 100_000))
          .configureStdout(true, true, true)
          .build();
      Executors.newSingleThreadExecutor().submit(() -> {
      });
      ps.stream().forEach(System.out::println);
    }
  }

  public static class PartitioningAndMerging extends TestUtils.TestBase {
    @Test(timeout = 10_000)
    public void testPartitioning() {
      ExecutorService threadPool = Executors.newFixedThreadPool(3);
      TestUtils.partition(dataStream("A", 10_000)).stream()
          .map((Stream<String> s) ->
              new ProcessStreamer.Builder(Shell.local(), "cat -n")
                  .stdin(s).build().stream())
          .forEach(s -> threadPool.submit(() -> s.forEach(System.out::println)));
      threadPool.shutdown();
    }

    @Test(timeout = 10_000)
    public void testPartitioner_100k() {
      ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), "cat -n")
          .stdin(dataStream("A", 100_000))
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
              .map(s -> new ProcessStreamer.Builder(Shell.local(), "cat -n").stdin(s).build().stream())
              .collect(Collectors.toList()))
          .forEach(System.out::println);
    }

  }

  private static List<String> runProcessStreamer(Supplier<ProcessStreamer> processStreamerSupplier)
      throws InterruptedException {
    ProcessStreamer ps = processStreamerSupplier.get();
    List<String> out = new LinkedList<>();
    ps.stream().forEach(out::add);
    System.out.println(ps.getPid() + "=" + ps.waitFor());
    return out;
  }
}
