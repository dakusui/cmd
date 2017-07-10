package com.github.dakusui.cmd;

public interface CmdObserver {
  void onFailure(CompatCmd upstream, RuntimeException upstreamException);
}
