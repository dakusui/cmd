package com.github.dakusui.cmd.core;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

public interface Tee<T> extends SplittingConnector<T> {
  default List<Stream<T>> tee() {
    return this.split();
  }

  class Builder<T> extends SplittingConnector.BaseBuilder<T, Tee<T>, Builder<T>> {
    public Builder(Stream<T> in) {
      super(in);
    }

    @Override
    public Tee<T> build() {
      return new Tee.Impl<>(threadPoolFactory.get(), numQueues, eachQueueSize, this.in());
    }
  }

  class Impl<T> extends SplittingConnector.Base<T> implements Tee<T> {
    Impl(ExecutorService threadPool, int numQueues, int eachQueueSize, Stream<T> in) {
      super(threadPool, numQueues, eachQueueSize, in);
    }

    @Override
    public List<Stream<T>> split() {
      return StreamUtils.tee(this.threadPool(), in, numQueues(), eachQueueSize());
    }
  }
}
