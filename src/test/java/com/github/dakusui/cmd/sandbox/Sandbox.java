package com.github.dakusui.cmd.sandbox;

import com.github.dakusui.cmd.Shell;
import com.github.dakusui.cmd.core.IoUtils;
import com.github.dakusui.cmd.core.ProcessStreamer;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
    IoUtils.merge(3,
        Stream.of("A", "B", "C", "D", "E", "F", "G", "H"),
        Stream.of("a", "b", "c", "d", "e", "f", "g", "h")
    ).forEach(System.out::println);
  }

  @Test
  public void testProcessStreamer1() throws InterruptedException {
    ProcessStreamer ps = new ProcessStreamer.Builder(Shell.local(), "cat -n").build();
    ps.stdin(Stream.of("a", "b", "c", null));
    new Thread(() -> {
      System.out.println("BEGIN");
      ps.stream().map(s -> "[" + s + "]").forEach(System.out::println);
      System.out.println("END");
    }).start();
    System.out.println(ps.waitFor());
    System.out.println(ps);
    System.out.println("bye");
  }

  @Test
  public void testProcessStreamer2() {
    new ProcessStreamer.Builder(Shell.local(), "echo hello world && echo !").build()
        .stream()
        .map(s -> "[" + s + "]")
        .forEach(System.out::println);
  }
}
