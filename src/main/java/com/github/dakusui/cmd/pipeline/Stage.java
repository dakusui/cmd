package com.github.dakusui.cmd.pipeline;

import java.util.LinkedList;
import java.util.List;

import static java.util.Objects.requireNonNull;

interface Stage<T> {
  Stage<T> connect(Stage downstream);

  class Impl<T> implements Stage<T> {
    private List<Stage<T>> downstreams = new LinkedList<>();

    @Override
    public Stage<T> connect(Stage downstream) {
      this.downstreams.add(requireNonNull(downstream));
      return this;
    }
  }
}


