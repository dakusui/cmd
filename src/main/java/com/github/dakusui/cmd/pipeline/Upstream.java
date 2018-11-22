package com.github.dakusui.cmd.pipeline;

import java.util.stream.Stream;

interface Upstream<T> extends Stage<T> {
  Pipe tee(Downstream... downstreams);
  Stream<T> stream();
}
