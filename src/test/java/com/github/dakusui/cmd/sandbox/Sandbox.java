package com.github.dakusui.cmd.sandbox;

import com.github.dakusui.cmd.Shell;
import com.github.dakusui.cmd.core.IoUtils;
import com.github.dakusui.cmd.core.ProcessStreamer;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static com.github.dakusui.crest.Crest.allOf;
import static com.github.dakusui.crest.Crest.asInteger;
import static com.github.dakusui.crest.Crest.asListOf;
import static com.github.dakusui.crest.Crest.assertThat;
import static com.github.dakusui.crest.Crest.call;
import static com.github.dakusui.crest.Crest.sublistAfter;
import static com.github.dakusui.crest.utils.printable.Predicates.containsString;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class Sandbox {

  @Test
  public void test() throws IOException, InterruptedException {
    // create a new list of arguments for our process
    List<String> list = new ArrayList<>();
    list.add("/bin/bash");
    list.add("-c");
    list.add("echo hello && echo_ hello");

    // create the process builder
    ProcessBuilder pb = new ProcessBuilder(list);
    //    pb.redirectErrorStream(true);
    //    pb.inheritIO();
    //    pb.redirectError(pb.redirectOutput());
    //    pb.redirectOutput(pb.redirectError());

    // get the command list
    System.out.println("" + pb.command());
    Process process = pb.start();
    print("stdout", process.getInputStream());
    print("stderr", process.getErrorStream());
    int exitCode = process.waitFor();
    System.out.println(exitCode);
  }

  private void print(String tag, InputStream inputStream) {
    /*
    try (BufferedReader br = bufferedReader(inputStream)) {
      String s;
      while ((s = readLineFrom(br)) != null)
        System.out.println(tag + ":" + s);
    }
    */
    IoUtils.stream(inputStream, Charset.defaultCharset()).map(s -> tag + ":" + s).forEach(System.out::println);
  }

  @Test(timeout = 3_000)
  public void testMerge() {
    IoUtils.merge(
        Executors.newFixedThreadPool(3),
        3,
        Stream.of("A", "B", "C", "D", "E", "F", "G", "H"),
        Stream.of("a", "b", "c", "d", "e", "f", "g", "h")
    ).forEach(System.out::println);
  }

  @Test
  public void testProcessStreamer1() throws InterruptedException {
    ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), "cat -n").build();
    ps.stdin(Stream.of("a", "b", "c", null));
    List<String> out = new LinkedList<>();
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    executorService.submit(() -> {
      ps.stream().map(s -> "[" + s + "]").forEach(out::add);
    });
    System.out.println(ps.waitFor());
    executorService.shutdown();
    while (!executorService.isTerminated()) {
      executorService.awaitTermination(1, MILLISECONDS);
    }
    assertThat(
        out,
        asListOf(String.class,
            sublistAfter(containsString("a"))
                .after(containsString("b"))
                .after(containsString("c")).$())
            .isEmpty().$()
    );
  }

  @Test
  public void testProcessStreamer1a() throws InterruptedException {
    ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), "cat -n").configureStderr(true, true, true).queueSize(1).ringBufferSize(1).build();
    ps.stdin(Stream.of("a", "b", "c", null));
    List<String> out = new LinkedList<>();
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    executorService.submit(() -> {
      ps.stream().map(s -> "[" + s + "]").forEach(out::add);
    });
    System.out.println(ps.waitFor());
    executorService.shutdown();
    while (!executorService.isTerminated()) {
      executorService.awaitTermination(1, MILLISECONDS);
    }
    assertThat(
        out,
        asListOf(String.class,
            sublistAfter(containsString("a"))
                .after(containsString("b"))
                .after(containsString("c")).$())
            .isEmpty().$()
    );
  }

  @Test
  public void testProcessStreamer2() {
    new ProcessStreamer.Builder(Shell.local(), "echo hello world && echo !").build()
        .stream()
        .map(s -> "[" + s + "]")
        .forEach(System.out::println);
  }

  @Test
  public void testProcessStreamer2E() {
    ProcessStreamer ps = new ProcessStreamer.Builder(
        Shell.local(),
        "echo hello world && Echo hello!").build();
    class Result {
      private int          exitCode;
      private List<String> out = new LinkedList<>();

      public int exitCode() {
        return this.exitCode;
      }

      public List<String> out() {
        return this.out;
      }
    }
    Result result = new Result();
    ps.stream().forEach(result.out::add);
    result.exitCode = ps.exitValue();

    System.out.println(ps + "=" + result.exitCode);
    result.out.forEach(System.out::println);

    assertThat(
        result,
        allOf(
            asListOf(String.class, call("out").$())
                .contains("sh: 1: Echo: not found")
                .contains("hello world").$(),
            asInteger("exitCode").eq(127).$()
        )
    );
  }
}
