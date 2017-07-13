package com.github.dakusui.cmd.ut;

import com.github.dakusui.cmd.Cmd;
import com.github.dakusui.cmd.StreamableQueue;
import com.github.dakusui.cmd.exceptions.CommandExecutionException;
import com.github.dakusui.cmd.utils.TestUtils;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.Cmd.cmd;
import static java.lang.String.format;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Sandbox {
  @Test(timeout = 3_000)
  public void streamExample1() {
    cmd(
        "echo hello"
    ).readFrom(
        Stream::empty
    ).stream(
    ).forEach(
        System.out::println
    );
  }

  @Test(timeout = 3_000)
  public void streamExample2() {
    cmd(
        "cat -n"
    ).readFrom(
        () -> Stream.of("Hello", "world")
    ).stream(
    ).forEach(
        System.err::println
    );
  }

  @Test(timeout = 3_000)
  public void streamExample3() {
    cmd(
        "echo hello && echo world"
    ).readFrom(
        Stream::empty
    ).pipeTo(
        cmd(
            "cat -n"
        )
    ).stream(
    ).forEach(
        System.out::println
    );
  }

  @Test(timeout = 3_000)
  public void streamExample4() {
    cmd(
        "echo hello && echo world"
    ).readFrom(
        Stream::empty
    ).pipeTo(
        cmd(
            "cat -n"
        ),
        cmd(
            "cat -n"
        )
    ).stream(
    ).forEach(
        System.out::println
    );
  }

  @Test(timeout = 5_000)
  public void streamExample5() {
    cmd(
        "echo world && echo hello"
    ).readFrom(
        Stream::empty
    ).pipeTo(
        cmd("sort").pipeTo(
            cmd("cat -n")
        )
    ).stream(
    ).forEach(
        System.out::println
    );
  }

  @Test(timeout = 5_000)
  public void streamExample6() {
    cmd(
        "echo world && echo hello"
    ).readFrom(
        Stream::empty
    ).pipeTo(
        cmd(
            "cat"
        ),
        cmd(
            "sort"
        ).pipeTo(cmd(
            "cat -n"
        ))
    ).stream(
    ).forEach(
        System.out::println
    );
  }

  @Test(timeout = 3_000, expected = CommandExecutionException.class)
  public void failingStreamExample1() {
    cmd(
        "unknownCommand hello"
    ).stream(
    ).forEach(
        System.out::println
    );
  }

  @Test(timeout = 5_000, expected = RuntimeException.class)
  public void failingStreamExample2() {
    cmd(
        "unknownCommand hello"
    ).pipeTo(
        cmd("cat -n")
    ).stream(
    ).forEach(
        System.out::println
    );
    System.out.println(format("Shouldn't be executed.(tid=%d)", Thread.currentThread().getId()));
  }

  @Ignore
  @Test
  public void failingStreamExample2b() {
    for (int i = 0; i < 100; i++) {
      System.out.println("=== " + i + " ===");
      Cmd cmd = cmd(
          "unknownCommand hello"
      ).pipeTo(
          cmd("cat -n")
      );
      if (!TestUtils.terminatesIn(
          () -> cmd.stream(
          ).forEach(
              System.out::println
          ),
          2_000
      )) {
        ((Cmd.Impl) cmd).dump(System.out);
        throw new RuntimeException();
      }
    }
  }

  @Test(timeout = 3_000, expected = CommandExecutionException.class)
  public void failingStreamExample3() {
    cmd(
        "echo hello"
    ).pipeTo(
        cmd("unknownCommand -n")
    ).stream(
    ).forEach(
        System.out::println
    );
  }

  @Test(timeout = 3_000, expected = CommandExecutionException.class)
  public void failingStreamExample4() {
    cmd(
        "echo hello"
    ).pipeTo(
        cmd("cat -n"),
        cmd("unknownCommand -n")
    ).stream(
    ).forEach(
        System.out::println
    );
  }

  @Test(timeout = 5_000)
  public void streamableQueue() {
    List<String> path = Collections.synchronizedList(new LinkedList<>());
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
      path.add("added:" + System.currentTimeMillis());
    }).start();
    assertFalse(TestUtils.terminatesIn(() -> queue.get().forEach(System.err::println), 2_000));
    path.add("retrieved:" + System.currentTimeMillis());
    System.out.println(path);
  }

  @Test(timeout = 5_000)
  public void streamableQueue2() {
    List<String> path = Collections.synchronizedList(new LinkedList<>());
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
      path.add("added:" + System.currentTimeMillis());
    }).start();
    assertTrue(TestUtils.terminatesIn(() -> queue.get().forEach(System.err::println), 2_000));
    path.add("retrieved:" + System.currentTimeMillis());
    System.out.println(path);
  }
}
