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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.Cmd.cmd;
import static com.github.dakusui.cmd.utils.TestUtils.allOf;
import static com.github.dakusui.cmd.utils.TestUtils.matcherBuilder;
import static java.lang.String.format;
import static org.junit.Assert.*;

public class Sandbox {
  private List<String> out = Collections.synchronizedList(new LinkedList<>());

  @Test(timeout = 3_000)
  public void streamExample1() {
    cmd(
        "echo hello"
    ).readFrom(
        Stream::empty
    ).stream(
    ).peek(
        System.out::println
    ).forEach(
        out::add
    );

    assertThat(
        out,
        allOf(
            TestUtils.<List<String>, Integer>matcherBuilder(
                "size", List::size
            ).check(
                "==1", size -> size == 1
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt0", o -> o.get(0)
            ).check(
                "=='hello'", "hello"::equals
            ).build()
        )
    );
  }

  @Test(timeout = 3_000)
  public void streamExample2() {
    cmd(
        "cat -n"
    ).readFrom(
        () -> Stream.of("Hello", "world")
    ).stream(
    ).peek(
        System.out::println
    ).forEach(
        out::add
    );

    assertThat(
        out,
        allOf(
            TestUtils.<List<String>, Integer>matcherBuilder(
                "size", List::size
            ).check(
                "==2", size -> size == 2
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt0", o -> o.get(0)
            ).check(
                "contains'1\tHello'", s -> s.contains("1\tHello")
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt1", o -> o.get(1)
            ).check(
                "contains'2\tworld'", s -> s.contains("2\tworld")
            ).build()
        )
    );
  }

  @Test(timeout = 3_000)
  public void streamExample3() {
    cmd(
        "echo Hello && echo world"
    ).readFrom(
        Stream::empty
    ).pipeTo(cmd(
        "cat -n"
    )).stream(
    ).peek(
        System.out::println
    ).forEach(
        out::add
    );

    assertThat(
        out,
        allOf(
            TestUtils.<List<String>, Integer>matcherBuilder(
                "size", List::size
            ).check(
                "==2", size -> size == 2
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt0", o -> o.get(0)
            ).check(
                "contains'1\tHello'", s -> s.contains("1\tHello")
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt1", o -> o.get(1)
            ).check(
                "contains'2\tworld'", s -> s.contains("2\tworld")
            ).build()
        )
    );

  }

  @Test(timeout = 3_000)
  public void streamExample4() {
    cmd(
        "echo Hello && echo world"
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
    ).peek(
        System.out::println
    ).forEach(
        out::add
    );

    assertThat(
        out.stream().sorted().collect(Collectors.toList()),
        allOf(
            TestUtils.<List<String>, Integer>matcherBuilder(
                "size", List::size
            ).check(
                "==4", size -> size == 4
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt0", o -> o.get(0)
            ).check(
                "contains'1\tHello'", s -> s.contains("1\tHello")
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt1", o -> o.get(1)
            ).check(
                "contains'1\tHello'", s -> s.contains("1\tHello")
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt2", o -> o.get(2)
            ).check(
                "contains'2\tworld'", s -> s.contains("2\tworld")
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt3", o -> o.get(3)
            ).check(
                "contains'2\tworld'", s -> s.contains("2\tworld")
            ).build()
        )
    );
  }

  @Test(timeout = 5_000)
  public void streamExample5() {
    cmd(
        "echo world && echo Hello"
    ).pipeTo(cmd(
        "sort"
    ).pipeTo(cmd(
        "cat -n"
    ))).readFrom(
        Stream::empty
    ).stream(
    ).peek(
        System.out::println
    ).forEach(
        out::add
    );

    assertThat(
        out.stream().sorted().collect(Collectors.toList()),
        allOf(
            TestUtils.<List<String>, Integer>matcherBuilder(
                "size", List::size
            ).check(
                "==2", size -> size == 2
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt0", o -> o.get(0)
            ).check(
                "contains'1\tHello'", s -> s.contains("1\tHello")
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt1", o -> o.get(1)
            ).check(
                "contains'2\tworld'", s -> s.contains("2\tworld")
            ).build()
        )
    );
  }

  @Test(timeout = 5_000)
  public void streamExample6() {
    cmd(
        "echo world && echo Hello"
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
    ).peek(
        System.out::println
    ).forEach(
        out::add
    );

    assertThat(
        out.stream().sorted().collect(Collectors.toList()),
        allOf(
            TestUtils.<List<String>, Integer>matcherBuilder(
                "size", List::size
            ).check(
                "==4", size -> size == 4
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt0", o -> o.get(0)
            ).check(
                "contains'1\tHello'", s -> s.contains("1\tHello")
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt1", o -> o.get(1)
            ).check(
                "contains'2\tworld'", s -> s.contains("2\tworld")
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt2", o -> o.get(2)
            ).check(
                "contains'Hello'", s -> s.contains("Hello")
            ).build(),
            TestUtils.<List<String>, String>matcherBuilder(
                "elementAt3", o -> o.get(3)
            ).check(
                "contains'world'", s -> s.contains("world")
            ).build()
        )
    );
  }

  // Flakiness was seen 7/13/2017
  @Test(timeout = 3_000, expected = CommandExecutionException.class)
  public void failingStreamExample1() {
    cmd(
        "unknownCommand hello"
    ).stream(
    ).peek(
        System.out::println
    ).forEach(
        out::add
    );

    assertThat(
        out.stream().sorted().collect(Collectors.toList()),
        allOf(
            matcherBuilder(
                "size", (Function<List<String>, Integer>) List::size
            ).check(
                "==2", size -> size == 2
            ).build(),
            matcherBuilder(
                "elementAt0", (List<String> o) -> o.get(0)
            ).check(
                "contains'1\tHello'", s -> s.contains("1\tHello")
            ).build(),
            matcherBuilder(
                "elementAt1", (List<String> o) -> o.get(1)
            ).check(
                "contains'2\tworld'", s -> s.contains("2\tworld")
            ).build()
        )
    );
  }

  /*
   * Flaky
   */
  @Test(timeout = 5_000, expected = CommandExecutionException.class)
  public void failingStreamExample2() {
    cmd(
        "unknownCommand hello"
    ).pipeTo(
        cmd("cat -n")
    ).stream(
    ).peek(
        System.out::println
    ).forEach(
        out::add
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
    ).peek(
        System.out::println
    ).forEach(
        out::add
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
    ).peek(
        System.out::println
    ).forEach(
        out::add
    );
  }

  @Test(timeout = 3_000, expected = CommandExecutionException.class)
  public void failingStreamExample5() {
    cmd(
        "echo hello"
    ).pipeTo(
        cmd("unknownCommand -n").pipeTo(
            cmd("cat -n")
        )
    ).stream(
    ).peek(
        System.out::println
    ).forEach(
        out::add
    );
  }

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
