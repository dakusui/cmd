package com.github.dakusui.cmd;

public interface CmdObserver {
  void closed(Cmd cmd);

  default void failed(Cmd cmd) {
    closed(cmd);
  }
}
