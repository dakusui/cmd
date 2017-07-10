package com.github.dakusui.cmd.ut;

import com.github.dakusui.cmd.core.Selector;
import com.github.dakusui.cmd.utils.TestUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;

public class SelectorTest extends TestUtils.TestBase {
  @Test(timeout = 5_000)
  public void given3Streams$whenSelect$thenAllElementsFoundInOutputAndInterleaved() {
    List<String> out = new LinkedList<>();
    ExecutorService executorService = Executors.newFixedThreadPool(3);
    try {
      Selector<String> selector = createSelector();
      selector.select().forEach(
          ((Consumer<String>) s -> {
            System.err.println("taken:" + s);
          }).andThen(
              out::add
          )
      );
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

  private Selector<String> createSelector() {
    return new Selector.Builder<String>()
        .add(list("A", 5).stream().filter(s -> sleepAndReturn(true)), s -> {
        }, true)
        .add(list("B", 10).stream().filter(s -> sleepAndReturn(true)), s -> {

        }, true)
        .add(list("C", 20).stream().filter(s -> sleepAndReturn(false)), s -> {
        }, true)
        .build();
  }

  static List<String> list(String prefix, int size) {
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
