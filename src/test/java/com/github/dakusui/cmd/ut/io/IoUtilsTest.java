package com.github.dakusui.cmd.ut.io;

import com.github.dakusui.cmd.compat.CompatIoUtils;
import org.junit.Test;

import java.util.stream.Stream;

public class IoUtilsTest {
  @Test(timeout = 10_000)
  public void givenSimpleConsumer$whenDoFlowControl$thenNotBlocked() {
    Stream.of("hello", "world").forEach(
        CompatIoUtils.flowControlValve(
            System.out::println,
            1
        ));
  }
}
