package com.github.dakusui.cmd.exceptions;

public class CommandExecutionException extends CommandException {
  public CommandExecutionException(String msg, Throwable t) {
    super(msg, t);
  }
}
