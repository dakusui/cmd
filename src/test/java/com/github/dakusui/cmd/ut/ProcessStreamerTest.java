package com.github.dakusui.cmd.ut;

import com.github.dakusui.cmd.core.process.ProcessStreamer;
import com.github.dakusui.cmd.core.process.Shell;
import com.github.dakusui.cmd.utils.TestUtils;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.core.process.ProcessStreamer.pipe;
import static com.github.dakusui.cmd.core.process.ProcessStreamer.sink;
import static com.github.dakusui.cmd.core.process.ProcessStreamer.source;
import static com.github.dakusui.cmd.utils.TestUtils.dataStream;
import static com.github.dakusui.crest.Crest.allOf;
import static com.github.dakusui.crest.Crest.asBoolean;
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
  public static class BuilderTest extends TestUtils.TestBase {
    @Test
    public void givenEnvVarHELLO_world_$whenEchoEnvVarHELLO$then_world_isPrinted() {
      ProcessStreamer ps = source().command("echo $HELLO")
          .env("HELLO", "world")
          .build();
      assertThat(
          ps.stream().collect(Collectors.joining()),
          asString().equalTo("world").$()
      );
    }

    @Test
    public void givenCwd$whenEchoEnvVarHELLO$then_world_isPrinted() {
      File dir = new File(System.getProperty("user.dir")).getParentFile();
      ProcessStreamer ps = source().command("pwd")
          .cwd(dir)
          .configureStdout(false, false, true)
          .build();
      assertThat(
          ps.stream().collect(Collectors.joining()),
          asString().equalTo(dir.getAbsolutePath()).$()
      );
    }

    @Test(expected = ProcessStreamer.Failure.class)
    public void givenCommandNotFound$whenStreamClosed$thenExceptionThrown() {
      String commandNotFound = "__command_not_found__";
      ProcessStreamer ps = source()
          .command(commandNotFound)
          .checker(ProcessStreamer.Checker.createDefault())
          .build();

      try (Stream<String> s = ps.stream()) {
        s.forEach(System.out::println);
      } catch (ProcessStreamer.Failure e) {
        assertThat(
            e.getMessage(),
            asString().containsString(commandNotFound).$()
        );
        throw e;
      }
    }
  }

  public static class LifeCycleMethodsTest extends TestUtils.TestBase {
    @Test(timeout = 1_000)
    public void givenSleepOneSecond$whenDestroy$thenEventuallyDead() {
      ProcessStreamer ps = source().command("sleep 1").build();
      ps.destroy();
      while (ps.isAlive()) {
        try {
          TimeUnit.MICROSECONDS.sleep(1);
        } catch (InterruptedException ignored) {
        }
      }
      assertThat(
          ps.isAlive(),
          asBoolean().isFalse().$()
      );
    }
  }

  public static class SinkTest extends TestUtils.TestBase {
    @Test(timeout = 3_000)
    public void testSink() throws IOException {
      ProcessStreamer ps = sink(dataStream("data-", 10_000))
          .command(String.format("cat -n > %s", File.createTempFile("processstreamer-", "tmp")))
          .build();
      ps.stream().forEach(System.out::println);
    }

    @Test(timeout = 3_000)
    public void testSinkBigger() throws IOException {
      ProcessStreamer ps = sink(dataStream("data-", 100_000))
          .command(String.format("cat -n > %s", File.createTempFile("processstreamer-", "tmp")))
          .build();
      ps.stream().forEach(System.out::println);
    }
  }

  public static class SourceTest extends TestUtils.TestBase {
    @Test(timeout = 20_000)
    public void givenEmptyStream$whenCommandThatWritesOneMilliOnLongLines$thenEventuallyFinishes() {
      source().command("for i in $(seq 1 1000); do seq 1 10000 | paste -s -d ' ' - ; done")
          .build()
          .stream()
          .forEach(System.out::println);
    }

    @Test(timeout = 10_000)
    public void givenStreamImmediatelyCloses$whenCommandWritingTenThousandLines$thenEventuallyFinishes() {
      source().command("seq 1 100000")
          .build()
          .stream().forEach(System.out::println);
    }

    @Test(timeout = 1_000)
    public void givenStreamImmediatelyCloses$whenCommandWritingOneThousandLines$thenEventuallyFinishes() {
      source(Shell.local()).command("seq 1 1000").build().stream().forEach(System.out::println);
    }

    @Test
    public void givenCommandResultingInError$whenExecuted$thenOutputIsCorrect() {
      class Result {
        private ProcessStreamer ps  = source()
            .command("echo hello world && _Echo hello!")
            .configureStderr(true, true, true)
            .checker(ProcessStreamer.Checker.createCheckerForExitCode(127))
            .build();
        private int             exitCode;
        private List<String>    out = new LinkedList<>();

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
              () -> sink(Stream.of("a", "b", "c"))
                  .command("echo hello world && echo !")
                  .build()),
          asListOf(String.class,
              sublistAfterElement("hello world").afterElement("!").$()).$());
    }

    @Test(timeout = 1_000, expected = ProcessStreamer.Failure.class)
    public void givenUnknownCommand$whenStream$thenFailureThrown() throws InterruptedException {
      runProcessStreamer(() -> ProcessStreamer.sink(Stream.of("a", "b", "c")).command("echo___ hello world && echo !").build());
    }
  }

  /**
   * Pipe test
   */
  public static class PipeTest extends TestUtils.TestBase {
    @Test(timeout = 1_000)
    public void givenSort$whenDrainDataAndClose$thenOutputIsCorrectAndInOrder() throws InterruptedException {
      assertThat(
          runProcessStreamer(() -> pipe(Stream.of("c", "b", "a")).command("sort").build()),
          asListOf(String.class,
              sublistAfter(containsString("a"))
                  .after(containsString("b"))
                  .after(containsString("c")).$())
              .isEmpty().$());
    }

    @Test(timeout = 1_000)
    public void givenSort$whenDrain1kDataAndClose$thenOutputIsCorrectAndInOrder() throws InterruptedException {
      assertThat(
          runProcessStreamer(() -> pipe(dataStream("data", 1_000), Shell.local()).command("sort").build()),
          asListOf(String.class,
              sublistAfter(containsString("997"))
                  .after(containsString("998"))
                  .after(containsString("999")).$())
              .isEmpty().$());
    }

    @Test(timeout = 180_000)
    public void givenSortPipedToCatN$whenDrainOneMillionLines$thenOutputIsCorrectAndInOrder() throws InterruptedException {
      int num = 1_000_000;
      assertThat(
          runProcessStreamer(() -> pipe(dataStream("data", num))
              .command("sort | cat -n")
              .build()),
          asInteger("size").equalTo(num).$());
    }

    @Test(timeout = 1_000)
    public void givenCatN$whenDrainData$thenOutputIsCorrectAndInOrder() throws InterruptedException {
      assertThat(
          runProcessStreamer(
              () -> pipe(Stream.of("a", "b", "c")).command("cat -n").build()),
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
              () -> ProcessStreamer.pipe(dataStream("data", lines)).command("cat -n").build()),
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
              () -> ProcessStreamer.pipe(Stream.of("a", "b", "c", "d", "e", "f", "g", "h"))
                  .command("cat -n")
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
      ProcessStreamer ps = pipe(dataStream("A", 10_000))
          .command("cat -n")
          .queueSize(1)
          .build();
      ps.stream().forEach(System.out::println);
    }

    @Test(timeout = 10_000)
    public void pipeTest100_000() {
      ProcessStreamer ps = pipe(dataStream("A", 100_000)).command("cat -n").build();
      Executors.newSingleThreadExecutor().submit(() -> {
      });
      ps.stream().forEach(System.out::println);
    }
  }

  private static List<String> runProcessStreamer(Supplier<ProcessStreamer> processStreamerSupplier)
      throws InterruptedException {
    ProcessStreamer ps = processStreamerSupplier.get();
    List<String> out = new LinkedList<>();
    try (Stream<String> stdout = ps.stream()) {
      stdout.forEach(out::add);
    }
    System.out.println("pid:" + ps.getPid() + "=exitCode:" + ps.waitFor());
    return out;
  }
}
