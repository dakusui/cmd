package com.github.dakusui.cmd.sandbox;

import com.github.dakusui.cmd.Shell;
import com.github.dakusui.cmd.core.ProcessStreamer;
import com.github.dakusui.cmd.utils.TestUtils;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.github.dakusui.crest.Crest.*;
import static com.github.dakusui.crest.utils.printable.Predicates.containsString;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ProcessStreamerTest extends TestUtils.TestBase {
  @Test(timeout = 1_000)
  public void givenCat$whenDrainDataAndClose$thenOutputIsCorrectAndInOrder() throws InterruptedException {
    assertThat(
        runProcessStreamer(
            () -> new ProcessStreamer.Builder(Shell.local(), "cat -n").build(),
            ps -> {
              ps.drain(Stream.of("a", "b", "c"));
              ps.close();
            }),
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
            ps -> {
              ps.drain(Stream.of("a", "b", "c"));
              ps.close();
            }),
        asListOf(String.class,
            sublistAfter(containsString("a"))
                .after(containsString("b"))
                .after(containsString("c")).$())
            .isEmpty().$());
  }

  @Test(timeout = 1_000)
  public void givenEchos$whenStream$thenOutputIsCorrectAndInOrder() throws InterruptedException {
    assertThat(
        runProcessStreamer(
            () -> new ProcessStreamer.Builder(Shell.local(), "echo hello world && echo !").build(),
            ps -> {
              ps.drain(Stream.of("a", "b", "c"));
              ps.close();
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
      private int exitCode;
      private List<String> out = new LinkedList<>();

      public int exitCode() {
        return this.exitCode;
      }

      public List<String> out() {
        return this.out;
      }

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
