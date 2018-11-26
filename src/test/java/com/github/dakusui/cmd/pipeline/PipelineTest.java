package com.github.dakusui.cmd.pipeline;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import static com.github.dakusui.crest.Crest.*;

public class PipelineTest implements Pipeline.Factory {

  @Test(timeout = 1_000)
  public void test() {
    List<String> out = Collections.synchronizedList(new LinkedList<>());
    Pipeline pipeline = cmd("echo hello && echo world");
    pipeline
        .tee(cmd("cat").map(String::toUpperCase),
            cmd("cat -n"),
            cmd("cat -n").stdin(Stream.of("Hello")))
        .stream().forEach(out::add);

    assertThat(
        out,
        allOf(
            asListOf(String.class, sublistAfterElement("HELLO").afterElement("WORLD").$()).$(),
            asListOf(String.class, sublistAfterElement("     1\thello").afterElement("     2\tworld").$()).$(),
            asListOf(String.class, sublistAfterElement("     1\tHello").$()).$()
        )
    );
  }

  @Test(timeout = 1_000)
  public void test2() {
    List<String> out = Collections.synchronizedList(new LinkedList<>());
    cmd("echo hello && echo world").stream().forEach(out::add);

    assertThat(
        out,
        asListOf(
            String.class,
            sublistAfterElement("hello")
                .afterElement("world").$()).$()
    );
  }

  @Test(timeout = 1_000)
  public void test3() {
    List<String> out = Collections.synchronizedList(new LinkedList<>());
    final Stream<String> s;
    (s = cmd("echo hello && echo world").tee(cmd("cat")).stream()).forEach(out::add);
    s.close();

    assertThat(
        out,
        asListOf(
            String.class,
            sublistAfterElement("hello")
                .afterElement("world").$()).$()
    );
  }
}