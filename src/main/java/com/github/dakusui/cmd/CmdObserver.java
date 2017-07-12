package com.github.dakusui.cmd;

import com.github.dakusui.cmd.tmp.CompatCmd;

public interface CmdObserver {
  void onFailure(CompatCmd upstream, RuntimeException upstreamException);
}
