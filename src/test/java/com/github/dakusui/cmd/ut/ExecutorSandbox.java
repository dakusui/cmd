package com.github.dakusui.cmd.ut;

import org.junit.Test;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class ExecutorSandbox {
  @Test
  public void shutdownBestPractice() throws InterruptedException {
    ExecutorService executor = Executors.newFixedThreadPool(1);
    executor.submit(new Runnable() {

      @Override
      public void run() {
        while (true) {
          if (Thread.currentThread().isInterrupted()) {
            System.out.println("interrupted");
            break;
          }
        }
      }
    });

    executor.shutdownNow();
    if (!executor.awaitTermination(100, TimeUnit.MILLISECONDS)) {
      System.out.println("Still waiting...");
      throw new RuntimeException();
    }
    System.out.println("Exiting normally...");
  }


  @Test
  public void test() {
    Stream.of(
        SelectorTest.list("A", 50),
        SelectorTest.list("B", 50),
        SelectorTest.list("C", 50)
    ).parallel(
    ).flatMap(
        Collection::stream
    ).forEach(
        System.out::println
    );
  }
}
