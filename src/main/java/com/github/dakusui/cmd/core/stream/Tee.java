package com.github.dakusui.cmd.core.stream;

import com.github.dakusui.cmd.utils.StreamUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
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
      return new Tee.Impl<>(threadPoolFactory, numQueues, eachQueueSize, this.in());
    }
  }

  class Impl<T> extends SplittingConnector.Base<T> implements Tee<T> {
    Impl(Supplier<ExecutorService> threadPoolFactory, int numQueues, int eachQueueSize, Stream<T> in) {
      super(threadPoolFactory, numQueues, eachQueueSize, in);
    }

    @Override
    public List<Stream<T>> split() {
      return StreamUtils.tee(this.threadPool(), in, numQueues(), eachQueueSize());
    }
  }
}
