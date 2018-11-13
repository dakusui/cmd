package com.github.dakusui.cmd.ut.io;

import com.github.dakusui.cmd.compat.CompatIoUtils;
import com.github.dakusui.cmd.core.IoUtils;
import com.github.dakusui.cmd.utils.TestUtils;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static com.github.dakusui.crest.Crest.*;

public class IoUtilsTest extends TestUtils.TestBase {
  @Test(timeout = 10_000)
  public void givenSimpleConsumer$whenDoFlowControl$thenNotBlocked() {
    Stream.of("hello", "world").forEach(
        CompatIoUtils.flowControlValve(
            System.out::println,
            1
        )
    );
  }

  @Test(timeout = 3_000)
  public void givenOneStream$whenMerge$thenOutputIsInOrder() {
    List<String> out = new LinkedList<>();
    IoUtils.merge(
        Executors.newFixedThreadPool(2),
        1,
        Stream.of("A", "B", "C", "D", "E", "F", "G", "H"))
        .peek(System.out::println)
        .forEach(out::add);

    assertThat(
        out,
        allOf(
            asListOf(String.class, sublistAfterElement("A").afterElement("H").$())
                .isEmpty().$(),
            asInteger("size").eq(8).$()));
  }

  @Test(timeout = 3_000)
  public void givenTwoStreams$whenMerge$thenOutputIsInOrder() {
    List<String> out = new LinkedList<>();
    IoUtils.merge(
        Executors.newFixedThreadPool(2),
        1,
        Stream.of("A", "B", "C", "D", "E", "F", "G", "H"),
        Stream.of("a", "b", "c", "d", "e", "f", "g", "h"))
        .peek(System.out::println)
        .forEach(out::add);

    assertThat(
        out,
        allOf(
            asListOf(String.class, sublistAfterElement("A").afterElement("H").$()).$(),
            asListOf(String.class, sublistAfterElement("a").afterElement("h").$()).$(),
            asInteger("size").eq(16).$()));
  }

}
