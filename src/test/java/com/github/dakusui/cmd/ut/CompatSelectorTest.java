package com.github.dakusui.cmd.ut;

import com.github.dakusui.cmd.core.CompatSelector;
import com.github.dakusui.cmd.utils.TestUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;

public class CompatSelectorTest extends TestUtils.TestBase {
  @Test(timeout = 5_000)
  public void given3Streams$whenSelect$thenAllElementsFoundInOutputAndInterleaved() {
    List<String> out = new LinkedList<>();
    ExecutorService executorService = Executors.newFixedThreadPool(3);
    try {
      CompatSelector<String> selector = createSelector(executorService);
      try {
        selector.select().forEach(
            ((Consumer<String>) s -> {
              System.err.println("taken:" + s);
            }).andThen(
                out::add
            )
        );
      } finally {
        selector.close();
      }
      out.forEach(System.out::println);
      //noinspection unchecked
      assertThat(out, CoreMatchers.allOf(
          TestUtils.<List<String>, Integer>matcherBuilder()
              .transform("sizeOf", List::size)
              .check("5+10==", u -> 5 + 10 == u)
              .build(),
          TestUtils.MatcherBuilder.<List<String>>simple()
              .check("interleaving", u -> !u.equals(u.stream().sorted().collect(toList())))
              .build()

      ));
    } finally {
      System.out.println("shutting down");
      executorService.shutdown();
    }
  }

  @Test(timeout = 15_000, expected = RuntimeException.class)
  public void given3Streams$whenSelectAndClosedAsync$thenFinishesIn10seconds() throws InterruptedException {
    List<String> out = new LinkedList<>();
    ExecutorService executorService = Executors.newFixedThreadPool(3);
    CompatSelector<String> selector = createSelector(executorService);
    try {
      new Thread(() -> {
        try {
          Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }
        selector.close();
      }).start();
      selector.select().forEach(
          ((Consumer<String>) s -> {
            if (Thread.currentThread().isInterrupted())
              throw new RuntimeException();
            System.err.println("taken:" + s);
          }).andThen(
              out::add
          )
      );
      out.forEach(System.out::println);
      ////
      // Since this is a liveness check, no assertion is necessary.
    } finally {
      System.out.println("shutting down:" + executorService);
      for (Thread thread : Thread.getAllStackTraces().keySet()) {
        System.out.println(thread.getState() + ":" + thread);
      }
      synchronized (selector.queue) {

        executorService.shutdownNow();
      }
      while (!executorService.awaitTermination(250, TimeUnit.MILLISECONDS)) {
        ThreadPoolExecutor executor = ((ThreadPoolExecutor) executorService);
        System.out.println("activeCount=" + executor.getActiveCount() + ":" + executor.getQueue());
      }
    }
  }

  private CompatSelector<String> createSelector(ExecutorService executorService) {
    return new CompatSelector.Builder<String>(3)
        .add(list("A", 5).stream().filter(s -> sleepAndReturn(true)))
        .add(list("B", 10).stream().filter(s -> sleepAndReturn(true)))
        .add(list("C", 20).stream().filter(s -> sleepAndReturn(false)))
        .withExecutorService(executorService)
        .build();
  }

  public static List<String> list(String prefix, int size) {
    List<String> ret = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      ret.add(String.format("%s-%s", prefix, i));
    }
    return ret;
  }

  private static boolean sleepAndReturn(boolean value) {
    if (value) {
      System.out.println("sleepAndReturn:start:" + Thread.currentThread().getId());
      if (Thread.currentThread().isInterrupted()) {
        System.out.println("sleepAndReturn:interrupted(1):" + Thread.currentThread().getId());
        return value;
      }
/*      try {
        Thread.sleep(1);
      } catch (InterruptedException ignored) {
        System.out.println("sleepAndReturn:interrupted(2):" + Thread.currentThread().getId());
      }
      */
      System.out.println("sleepAndReturn:end:" + Thread.currentThread().getId());
    }
    return value;
  }
}
