package com.github.dakusui.cmd.ut;

import com.github.dakusui.cmd.core.Selector;
import com.github.dakusui.cmd.utils.TestUtils;
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
      Selector<String> selector = createSelector(50, 100, 200);
      selector.stream().forEach(
          ((Consumer<String>) s -> {
            System.err.println("taken:" + s);
          }).andThen(
              out::add
          )
      );
      out.forEach(System.out::println);
      //noinspection unchecked
      assertThat(
          out,
          TestUtils.allOf(
              TestUtils.<List<String>, Integer>matcherBuilder()
                  .transform("sizeOf", List::size)
                  .check("50+100==", u -> 50 + 100 == u)
                  .build(),
              TestUtils.MatcherBuilder.<List<String>>simple()
                  .check("interleaving", u -> !u.equals(u.stream().sorted().collect(toList())))
                  .build()

          ));
    } finally {
      System.out.println("shutting to");
      executorService.shutdown();
    }
  }

  private Selector<String> createSelector(int sizeA, int sizeB, int sizeC) {
    return new Selector.Builder<String>()
        .add(list("A", sizeA).stream().filter(s -> sleepAndReturn(true)), s -> {
        }, true)
        .add(list("B", sizeB).stream().filter(s -> sleepAndReturn(true)), s -> {

        }, true)
        .add(list("C", sizeC).stream().filter(s -> sleepAndReturn(false)), s -> {
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
