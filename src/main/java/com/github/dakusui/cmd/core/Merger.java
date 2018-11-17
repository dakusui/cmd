package com.github.dakusui.cmd.core;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

public interface Merger<T> extends Connector<T> {
  @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    @Override
    public Merger<T> build() {
      return new Merger.Impl<T>(streams, threadPoolFactory.get(), numQueues, eachQueueSize);
    }
  }

  class Impl<T> extends Connector.Base<T> implements Merger<T> {
    private final List<Stream<T>> streams;

    public Impl(List<Stream<T>> streams, ExecutorService threadPool, int numQueues, int eachQueueSize) {
      super(threadPool, numQueues, eachQueueSize);
      this.streams = requireNonNull(streams);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Stream<T> merge() {
      return StreamUtils.merge(
          threadPool(),
          eachQueueSize(),
          this.streams.toArray(new Stream[0])
      );
    }

    @Override
    public List<Stream<T>> streams() {
      return this.streams;
    }
  }
}
