package com.github.dakusui.cmd.ut;

import com.github.dakusui.cmd.pipeline.Pipeline;
import com.github.dakusui.cmd.utils.Repeat;
import com.github.dakusui.cmd.utils.RepeatRule;
import com.github.dakusui.cmd.utils.TestUtils;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.utils.TestUtils.dataStream;
import static com.github.dakusui.crest.Crest.allOf;
import static com.github.dakusui.crest.Crest.asInteger;
import static com.github.dakusui.crest.Crest.asListOf;
import static com.github.dakusui.crest.Crest.assertThat;
import static com.github.dakusui.crest.Crest.sublistAfterElement;

public class PipelineTest extends TestUtils.TestBase implements Pipeline.Factory {
  @Rule
  public RepeatRule repeatRule = new RepeatRule();

  @Test(timeout = 1_000)
  public void test() {
    List<String> out = Collections.synchronizedList(new LinkedList<>());

    Stream<String> stream = cmd("echo hello && echo world")
        .connect(cmd("cat").map(8, String::toUpperCase),
            cmd("cat -n"),
            cmd("cat -n").stdin(Stream.of("Hello")))
        .stream();
    stream.forEach(out::add);

    assertThat(
        out,
        allOf(
            asListOf(String.class, sublistAfterElement("HELLO").afterElement("WORLD").$()).$(),
            asListOf(String.class, sublistAfterElement("     1\thello").afterElement("     2\tworld").$()).$(),
            asListOf(String.class, sublistAfterElement("     1\tHello").$()).$()
        )
    );
    out.forEach(System.out::println);
    System.out.println(Thread.getAllStackTraces().keySet().size());
  }

  @Test(timeout = 1_000)
  @Repeat(times = 1_000)
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
    System.out.println(Thread.getAllStackTraces().keySet().size());
  }

  @Test(timeout = 4_500)
  @Repeat(times = 1_000)
  public void test3() {
    List<String> out = Collections.synchronizedList(new LinkedList<>());
    cmd("echo hello && echo world").connect(cmd("cat")).stream().forEach(out::add);

    assertThat(
        out,
        asListOf(
            String.class,
            sublistAfterElement("hello")
                .afterElement("world").$()).$()
    );

    System.out.println(Thread.getAllStackTraces().keySet().size());
  }


  @Test(timeout = 10_000)
  public void test4() {
    int before = Thread.getAllStackTraces().keySet().size();
    System.out.println("before=" + before);
    List<String> out = Collections.synchronizedList(new LinkedList<>());

    try (Stream<String> stream = cmd("cat")
        .stdin(dataStream("data", 10_000))
        .map(8, String::toUpperCase).stream()) {
      stream.peek(System.out::println).forEach(out::add);
    }

    int after = Thread.getAllStackTraces().keySet().size();
    System.out.println("after=" + after);

    assertThat(
        out,
        allOf(
            asListOf(String.class, sublistAfterElement("DATA-9999").$()).$(),
            asInteger("size").equalTo(10000).$()
        ));
  }
}