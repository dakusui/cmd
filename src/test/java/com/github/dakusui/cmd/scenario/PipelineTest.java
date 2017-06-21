package com.github.dakusui.cmd.scenario;

import com.github.dakusui.cmd.Cmd;
import com.github.dakusui.cmd.Shell;
import com.github.dakusui.cmd.core.StreamableProcess;
import com.github.dakusui.cmd.utils.TestUtils;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PipelineTest extends TestUtils.TestBase {
  @Test(timeout = 3_000)
  public void givenCommandPipeline$whenRunIt$thenAllDataProcessed() {
    List<String> out = new LinkedList<>();
    Cmd.cmd(
        Shell.local(),
        "echo hello && echo world"
    ).connect(
        "cat -n"
    ).connect(
        "sort -r"
    ).connect(
        "sed 's/hello/HELLO/'"
    ).connect(
        "sed -E 's/^ +//'"
    ).stream(
    ).map(
        s -> String.format("<%s>", s)
    ).forEach(
        out::add
    );
    assertEquals("<2\tworld>", out.get(0));
    assertEquals("<1\tHELLO>", out.get(1));
  }

  @Test(timeout = 3_000, expected = RuntimeException.class)
  public void failingCommand() {
    Cmd.cmd(
        Shell.local(),
        "cat non-existing-file"
    ).stream(
    ).forEach(
        System.out::println
    );
  }

  @Test(timeout = 3_000)
  public void passingCommand() {
    Cmd.cmd(
        Shell.local(),
        "echo Hello!!!"
    ).stream(
    ).forEach(
        System.out::println
    );
  }

  @Test(timeout = 5_000, expected = RuntimeException.class)
  public void failingCommandConnectedToNextCommand() {
    Cmd.cmd(
        Shell.local(),
        "cat non-existing-file"
    ).connect(
        Shell.local(),
        stdio -> new StreamableProcess.Config.Builder(stdio).configureStdout(System.out::println).build(),
        "cat -n"
    ).stream(
    ).forEach(
        System.out::println
    );
  }

  @Test(timeout = 3_000, expected = RuntimeException.class)
  public void failingCommandConnectedToNextTwoCommands() {
    Cmd.cmd(
        Shell.local(),
        "cat non-existing-file"
    ).connect(
        Shell.local(),
        stdio -> new StreamableProcess.Config.Builder(stdio).configureStdout(System.out::println).build(),
        "cat -n"
    ).connect(
        Shell.local(),
        stdio -> new StreamableProcess.Config.Builder(stdio).configureStdout(System.out::println).build(),
        "cat -n"
    ).stream(
    ).forEach(
        System.out::println
    );
  }

  @Test(timeout = 3_000)
  public void givenCmd$whenGetProcessConfig$thenReturnedObjectSane() {
    Cmd cmd = Cmd.cmd(Shell.local(), "echo hello");
    StreamableProcess.Config processConfig = cmd.getProcessConfig();

    System.out.println(processConfig.charset());
    assertEquals(Charset.defaultCharset(), processConfig.charset());
  }

}
