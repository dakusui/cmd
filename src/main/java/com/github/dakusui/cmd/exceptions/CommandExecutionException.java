package com.github.dakusui.cmd.exceptions;

public class CommandExecutionException extends CommandException {
  CommandExecutionException(String msg, Throwable t) {
    super(msg, t);
  }

  public static CommandExecutionException upstreamFailed() {
    throw new CommandExecutionException("Upstream command failed", null);
  }
}
