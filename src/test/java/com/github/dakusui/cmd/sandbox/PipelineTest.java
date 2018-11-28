package com.github.dakusui.cmd.sandbox;

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
import static com.github.dakusui.crest.Crest.sublistAfter;
import static com.github.dakusui.crest.Crest.sublistAfterElement;
import static com.github.dakusui.crest.utils.printable.Predicates.containsString;

public class PipelineTest extends TestUtils.TestBase implements Pipeline.Factory {
  @Rule
  public RepeatRule repeatRule = new RepeatRule();

  @Test(timeout = 1_000)
  //  @Repeat(times = 1_000)
  public void test() {
    List<String> out = Collections.synchronizedList(new LinkedList<>());

    try (Stream<String> stream = cmd("echo hello && echo world")
        .connect(cmd("cat").map(8, String::toUpperCase),
            cmd("cat -n"),
            cmd("cat -n").stdin(Stream.of("Hello")))
        .stream()) {
      stream.forEach(out::add);
    }

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
    try (final Stream<String> s = cmd("echo hello && echo world").stream()) {
      s.forEach(out::add);
    }

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
    final Stream<String> s;
    (s = cmd("echo hello && echo world").connect(cmd("cat")).stream()).forEach(out::add);
    s.close();

    assertThat(
        out,
        asListOf(
            String.class,
            sublistAfterElement("hello")
                .afterElement("world").$()).$()
    );

    System.out.println(Thread.getAllStackTraces().keySet().size());
  }


  @Test
  public void test4() {
    int before = Thread.getAllStackTraces().keySet().size();
    System.out.println("before=" + before);
    List<String> out = Collections.synchronizedList(new LinkedList<>());

    try (Stream<String> stream = cmd("cat").stdin(dataStream("data", 10_000)).map(8, String::toUpperCase).stream()) {
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

  @Test(timeout = 10_000)
  public void test5() {
    int before = Thread.getAllStackTraces().keySet().size();
    System.out.println("before=" + before);
    List<String> out = Collections.synchronizedList(new LinkedList<>());

    try (Stream<String> stream = cmd("cat").stdin(Stream.concat(dataStream("data", 100_000), Stream.of((String)null)))
        .map(8, cmd("sort -S 10M | cat -n"))
        .stream()) {
      stream.peek(System.out::println).forEach(out::add);
    } finally {
      int after = Thread.getAllStackTraces().keySet().size();
      System.out.println("after=" + after);
      Thread.getAllStackTraces().keySet().forEach(System.out::println);
    }

    assertThat(
        out,
        allOf(
            asListOf(String.class, sublistAfter(containsString("data-0")).$()).$(),
            asListOf(String.class, sublistAfter(containsString("data-99999")).$()).$(),
            asInteger("size").equalTo(100_000).$()
        ));
  }

}