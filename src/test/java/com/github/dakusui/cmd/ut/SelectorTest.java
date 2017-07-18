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

import static com.github.dakusui.cmd.core.Selector.select;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;

public class SelectorTest extends TestUtils.TestBase {
  /**
   * TODO: Flakiness was seen (7/18/2017)
   * <pre>
   *   B-99
   * shutting to
   *
   * java.lang.NullPointerException
   * at java.util.LinkedList$ListItr.next(LinkedList.java:893)
   * at java.lang.Iterable.forEach(Iterable.java:74)
   * at com.github.dakusui.cmd.ut.SelectorTest.given3Streams$whenSelect$thenAllElementsFoundInOutputAndInterleaved(SelectorTest.java:31)
   * at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
   * at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
   * at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
   * at java.lang.reflect.Method.invoke(Method.java:497)
   * at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)
   * at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
   * at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)
   * at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)
   * at org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)
   * at org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)
   * at java.util.concurrent.FutureTask.run(FutureTask.java:266)
   * at java.lang.Thread.run(Thread.java:745)
   *
   *
   * </pre>
   */
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

  @Test(timeout = 5_000)
  public void select10Kdata() {
    select(
        SelectorTest.<String>list("A", 100_000).stream(),
        SelectorTest.<String>list("B", 100_000).stream(),
        SelectorTest.<String>list("C", 100_000).stream()
    ).forEach(System.out::println);
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

  private static List<String> list(String prefix, int size) {
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
