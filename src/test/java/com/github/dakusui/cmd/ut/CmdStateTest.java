package com.github.dakusui.cmd.ut;

import com.github.dakusui.cmd.CompatCmd;
import com.github.dakusui.cmd.Shell;
import com.github.dakusui.cmd.utils.TestUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;

public class CmdStateTest extends TestUtils.TestBase {
  @Test(expected = IllegalStateException.class)
  public void givenCmdNotStarted$whenExitValue$thenIllegalStateWillBeThrown() {
    CompatCmd cmd = CompatCmd.cmd(Shell.local(), "echo hello");
    try {
      cmd.exitValue();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage(), CoreMatchers.containsString("Current state=<NOT_STARTED>"));
      throw e;
    }
  }

  @Test(expected = IllegalStateException.class)
  public void givenCmdNotStarted$whenDestroy$thenIllegalStateWillBeThrown() {
    CompatCmd cmd = CompatCmd.cmd(Shell.local(), "echo hello");
    try {
      cmd.abort();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage(), CoreMatchers.containsString("Current state=<NOT_STARTED>"));
      throw e;
    }
  }

  @Test(expected = IllegalStateException.class)
  public void givenCmdNotStarted$whenGetPid$thenIllegalStateWillBeThrown() {
    CompatCmd cmd = CompatCmd.cmd(Shell.local(), "echo hello");
    try {
      cmd.getPid();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage(), CoreMatchers.containsString("Current state=<NOT_STARTED>"));
      throw e;
    }
  }

  @Test(expected = IllegalStateException.class)
  public void givenCmdAlreadyRun$whenRunAgain$thenIllegalStateWillBeThrown() {
    CompatCmd cmd = CompatCmd.cmd(Shell.local(), "echo hello");
    cmd.stream().forEach(System.out::println);
    try {
      cmd.stream();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage(), CoreMatchers.containsString("Current state=<CLOSED>"));
      throw e;
    }
  }
}
