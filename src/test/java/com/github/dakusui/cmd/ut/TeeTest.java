package com.github.dakusui.cmd.ut;

import com.github.dakusui.cmd.core.Tee;
import com.github.dakusui.cmd.utils.TestUtils;
import com.github.dakusui.jcunit8.factorspace.Parameter;
import com.github.dakusui.jcunit8.runners.junit4.JCUnit8;
import com.github.dakusui.jcunit8.runners.junit4.annotations.Condition;
import com.github.dakusui.jcunit8.runners.junit4.annotations.From;
import com.github.dakusui.jcunit8.runners.junit4.annotations.Given;
import com.github.dakusui.jcunit8.runners.junit4.annotations.ParameterSource;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertThat;

@RunWith(JCUnit8.class)
public class TeeTest {
  @ParameterSource
  public Parameter.Factory<List<String>> data() {
    return Parameter.Simple.Factory.of(asList(
        asList("1", "2", "3"),
        asList("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"),
        emptyList()
    ));
  }

  @ParameterSource
  public Parameter.Factory<Integer> numStreams() {
    return Parameter.Simple.Factory.of(asList(
        1, 2, 3, 4//, 5, 10
    ));
  }

  @ParameterSource
  public Parameter.Factory<Integer> queueSize() {
    return Parameter.Simple.Factory.of(asList(
        1, 2, 8192//, 5, 10, 100
    ));
  }

  @Test
  public void printTestCase(
      @From("data") List<String> data,
      @From("numStreams") int numStreams,
      @From("queueSize") int queueSize
  ) {
    System.err.printf("data=%s,numStreams=%s,queueSize=%s%n", data, numStreams, queueSize);
  }

  @Test(timeout = 1_000)
  @Given("isDataEmpty")
  public void givenEmptyData$whenDoTee$thenNothingIsInOutput(
      @From("data") List<String> data,
      @From("numStreams") int numStreams,
      @From("queueSize") int queueSize
  ) {
    assertThat(
        runTee(data, numStreams, queueSize),
        TestUtils.<List<String>, Integer>matcherBuilder()
            .transform("size", List::size)
            .check("==0", size -> size == 0).build()
    );
  }

  @Test(timeout = 2_000)
  @Given("!isDataEmpty&&isQueueSizeBig")
  public void givenNonEmptyData$whenDoTeeWithBigQueueSize$thenRunsNormally(
      @From("data") List<String> data,
      @From("numStreams") int numStreams,
      @From("queueSize") int queueSize
  ) {
    runTee(data, numStreams, queueSize);
  }

  @Test(timeout = 5_000)
  @Given("!isDataEmpty&&!isQueueSizeBig")
  public void givenNonEmptyData$whenDoTee$thenRunsNormally(
      @From("data") List<String> data,
      @From("numStreams") int numStreams,
      @From("queueSize") int queueSize
  ) {
    runTee(data, numStreams, queueSize);
  }

  @Condition
  public boolean isDataEmpty(@From("data") List<String> data) {
    return data.isEmpty();
  }

  @Condition
  public boolean isQueueSizeBig(@From("queueSize") int queueSize) {
    return queueSize > 5;
  }

  private List<String> runTee(
      List<String> data,
      int numStreams,
      int queueSize
  ) {
    List<String> output = Collections.synchronizedList(new LinkedList<>());
    new Tee.Builder<>(
        data.stream()
    ).setNumberOfStreams(
        numStreams
    ).setQueueSize(
        queueSize
    ).connect()
        .parallelStream()
        .forEach(
            (Stream<String> stream) ->
                stream.map(
                    s -> (String.format("%02d", Thread.currentThread().getId()) + ":" + s)
                ).forEach(
                    output::add
                )
        );
    return output;
  }
}
