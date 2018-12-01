package com.github.dakusui.cmd.core.stream;

import com.github.dakusui.cmd.utils.StreamUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

public interface Merger<T> extends Connector<T> {
  Stream<T> merge();

  class Builder<T> extends Connector.BaseBuilder<T, Merger<T>, Builder<T>> {
    private final List<Stream<T>> streams;

    public Builder(List<Stream<T>> streams) {
      super();
      this.streams = requireNonNull(streams);
    }

    @SafeVarargs
    public Builder(Stream<T>... streams) {
      this(asList(streams));
    }

    @Override
    public Merger<T> build() {
      return new Merger.Impl<>(streams, threadPoolFactory, numQueues, eachQueueSize);
    }
  }

  class Impl<T> extends Connector.Base<T> implements Merger<T> {
    private final List<Stream<T>> streams;

    Impl(List<Stream<T>> streams, Supplier<ExecutorService> threadPoolFactory, int numQueues, int eachQueueSize) {
      super(threadPoolFactory, numQueues, eachQueueSize);
      this.streams = requireNonNull(streams);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Stream<T> merge() {
      return (Stream<T>) StreamUtils.closeOnFinish(StreamUtils.merge(
          threadPool(),
          Base::shutdownThreadPoolAndWaitForTermination,
          eachQueueSize(),
          this.streams.toArray(new Stream[0])
      ));
    }

  }
}
