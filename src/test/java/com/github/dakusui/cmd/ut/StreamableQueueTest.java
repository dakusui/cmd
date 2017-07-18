package com.github.dakusui.cmd.ut;

import com.github.dakusui.cmd.StreamableQueue;
import com.github.dakusui.cmd.utils.TestUtils;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.github.dakusui.cmd.utils.TestUtils.allOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class StreamableQueueTest {
  private List<String> out = Collections.synchronizedList(new LinkedList<>());

  @Test(timeout = 5_000)
  public void streamableQueue() {
    StreamableQueue<String> queue = new StreamableQueue<>(100);
    new Thread(() -> {
      try {
        Thread.sleep(1_000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      System.out.println("adding");
      queue.accept("Hello");
      //      queue.accept(null);
      out.add("added");
    }).start();

    assertFalse(TestUtils.terminatesIn(() -> queue.get().peek(out::add).forEach(System.err::println), 2_000));

    out.add("retrieved");

    assertThat(
        out,
        allOf(
            TestUtils.<List<String>, Integer>matcherBuilder(
                "size", List::size
            ).check(
                "==3", size -> size == 3
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt0", o -> o.get(0)
            ).check(
                "=='added'", s -> s.equals("added")
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt1", o -> o.get(1)
            ).check(
                "=='Hello'", s -> s.equals("Hello")
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt2", o -> o.get(2)
            ).check(
                "=='retrieved'", s -> s.equals("retrieved")
            ).build()
        )
    );
  }

  @Test(timeout = 5_000)
  public void streamableQueue2() {
    StreamableQueue<String> queue = new StreamableQueue<>(100);
    new Thread(() -> {
      try {
        Thread.sleep(1_000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      System.out.println("adding");
      queue.accept("Hello");
      queue.accept(null);
      out.add("added");
    }).start();
    assertTrue(TestUtils.terminatesIn(() -> queue.get().peek(System.out::println).forEach(out::add), 2_000));
    out.add("retrieved");

    assertThat(
        out,
        allOf(
            TestUtils.<List<String>, Integer>matcherBuilder(
                "size", List::size
            ).check(
                "==3", size -> size == 3
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt0", o -> o.get(0)
            ).check(
                "=='added'", s -> s.equals("added")
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt1", o -> o.get(1)
            ).check(
                "=='Hello'", s -> s.equals("Hello")
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt2", o -> o.get(2)
            ).check(
                "=='retrieved'", s -> s.equals("retrieved")
            ).build()
        )
    );

  }
}
