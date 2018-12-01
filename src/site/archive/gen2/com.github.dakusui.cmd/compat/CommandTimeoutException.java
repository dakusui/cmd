package com.github.dakusui.cmd.compat;

import com.github.dakusui.cmd.exceptions.CommandException;

@Deprecated
public class CommandTimeoutException extends CommandException {
  public CommandTimeoutException(Throwable t) {
    super(t);
  }
}
