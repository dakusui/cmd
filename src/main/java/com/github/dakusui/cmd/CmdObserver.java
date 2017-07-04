package com.github.dakusui.cmd;

public interface CmdObserver {
  void onFailure(Cmd upstream, RuntimeException upstreamException);
}
