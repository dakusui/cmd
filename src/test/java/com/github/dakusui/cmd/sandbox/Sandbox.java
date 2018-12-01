package com.github.dakusui.cmd.sandbox;

import org.junit.Test;

import java.util.stream.Stream;

public class Sandbox {
  @Test
  public void exceptionFromOnClose() {
    Stream<String> s = Stream.of("a","b","c").onClose(() -> {
      synchronized ("") {
        try {
          Thread.sleep(1000);
          System.out.println("hello");
        } catch (InterruptedException e) {
          throw new RuntimeException();
        }
      }
    });
    s.forEach(System.out::println);
    s.close();
    System.out.println("world");
  }
}
