package com.github.dakusui.cmd.sandbox;

import com.github.dakusui.cmd.Shell;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
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
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@RunWith(Enclosed.class)
public class ProcessStreamerTest extends TestUtils.TestBase {
  public static class SinkTest {
    @Test(timeout = 3_000)
    public void testSink() throws IOException {
      ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), String.format("cat -n > %s", File.createTempFile("processstreamer-", "tmp")))
          .configureStdout(false, false, false)
          .build();
      ps.drain(dataStream("data-", 10_000));
      ps.stream().forEach(System.out::println);
    }

    @Test(timeout = 3_000)
    public void testSinkBigger() throws IOException {
      ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), String.format("cat -n > %s", File.createTempFile("processstreamer-", "tmp")))
          .configureStdout(false, false, false)
          .build();
      ps.drain(dataStream("data-", 100_000));
      ps.stream().forEach(System.out::println);
    }
  }

  public static class SourceTest {
    @Test(timeout = 20_000)
    public void testSource() {
      ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(),
          "for i in $(seq 1 1000); do seq 1 10000 | paste -s -d ' ' - ; done")
          .configureStdout(true, true, true)
          .build();
      ps.drain(Stream.empty());
      ps.stream().forEach(System.out::println);
    }

    @Test(timeout = 3_000)
    public void testSourceManyLines() {
      ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), "seq 1 100000").configureStdout(true, true, true)
          .build();
      ps.drain(Stream.empty());
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
              () -> new ProcessStreamer.Builder(Shell.local(), "echo hello world && echo !").build(),
              ps -> {
                ps.drain(Stream.of("a", "b", "c"));
              }),
          asListOf(String.class,
              sublistAfterElement("hello world").afterElement("!").$()).$());
    }
  }

  /**
   * Pipe test
   */
  public static class PipeTest {
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
      int lines = 100_000;
      assertThat(
          runProcessStreamer(
              () -> new ProcessStreamer.Builder(Shell.local(), "cat -n").configureStdout(true, true, true).build(),
              ps -> ps.drain(dataStream("data", lines))),
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

    @Test(timeout = 5_000)
    public void testPartitioning() {
      TestUtils.partition(
          dataStream("A", 10_000)).stream()
          .map(s -> {
            ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), "cat -n").build();
            ps.drain(s);
            return ps.stream();
          })
          .forEach(s -> s.forEach(System.out::println));
    }

    @Test(timeout = 5_000)
    public void testPartitioningAndMerging() {
      TestUtils.merge(
          TestUtils.partition(
              dataStream("A", 10_000)).stream()
              .map(s -> {
                ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), "cat -n").build();
                ps.drain(s);
                return ps.stream();
              })
              .collect(Collectors.toList()))
          .forEach(System.out::println);
    }

    @Test(timeout = 1_000)
    public void pipeTest() {
      ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), "cat -n").build();
      AtomicBoolean isReady = new AtomicBoolean(false);
      Executors.newSingleThreadExecutor().submit(() -> ps.drain(dataStream("A", 10_000)));
      ps.stream().forEach(System.out::println);
    }

    @Test(timeout = 10_000)
    public void pipeTest100_000() {
      ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), "cat -n")
          .configureStdout(true, true, true)
          .build();
      Executors.newSingleThreadExecutor().submit(() -> ps.drain(dataStream("A", 100_000)));
      ps.stream().forEach(System.out::println);
    }
  }

  private static List<String> runProcessStreamer(
      Supplier<ProcessStreamer> processStreamerSupplier,
      Consumer<ProcessStreamer> dataDrainer) throws InterruptedException {
    ProcessStreamer ps = processStreamerSupplier.get();
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    executorService.submit(() -> dataDrainer.accept(ps));
    List<String> out = new LinkedList<>();
    executorService.submit(() -> ps.stream().forEach(out::add));
    System.out.println(ps.getPid() + "=" + ps.waitFor());
    executorService.shutdown();
    while (!executorService.isTerminated()) {
      executorService.awaitTermination(1, MILLISECONDS);
    }
    return out;
  }
}
