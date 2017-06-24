package com.github.dakusui.cmd.ut;

import org.junit.Test;

import java.util.stream.Stream;

public class Sandbox {
  @Test
  public void testClose() {
    Stream<String> s = Stream.of("hello", "world").onClose(new Runnable() {
      @Override
      public void run() {
        System.out.println("CLOSED");
      }
    });
    s.forEach(System.out::println);
  }
}
