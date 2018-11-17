package com.github.dakusui.cmd.core;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

public interface Partitioner<T> extends Connector<T> {
  List<Stream<T>> partition();

  class Builder<T> extends Connector.BaseBuilder<T, Partitioner<T>, Builder<T>> {
    private final Stream<T> in;
    Function<T, Integer> partitioningFunction;

    public Builder(Stream<T> in) {
      this.in = requireNonNull(in);
      this.eachQueueSize(100)
          .numQueues(min(Runtime.getRuntime().availableProcessors() - 1, 1))
          .partitioningFunction(Object::hashCode)
          .threadPoolFactory(() -> Executors.newFixedThreadPool(this.numQueues + 1));
    }


    public Builder<T> partitioningFunction(Function<T, Integer> partitioningFunction) {
      this.partitioningFunction = requireNonNull(partitioningFunction);
      return this;
    }

    public Partitioner<T> build() {
      return new Partitioner.Impl<>(this.in, threadPoolFactory.get(), numQueues, eachQueueSize, partitioningFunction);
    }
  }

  class Impl<T> extends Connector.Base<T> implements Partitioner<T> {
    private final Stream<T>            in;
    private final Function<T, Integer> partitioningFunction;

    Impl(Stream<T> in, ExecutorService threadPool, int numQueues, int eachQueueSize, Function<T, Integer> partitioningFunction) {
      super(threadPool, numQueues, eachQueueSize);
      this.in = requireNonNull(in);
      this.partitioningFunction = partitioningFunction;
    }

    @Override
    public List<Stream<T>> streams() {
      return this.partition();
    }

    @Override
    public List<Stream<T>> partition() {
      return StreamUtils.partition(this.threadPool(), in, numQueues(), eachQueueSize(), partitioningFunction);
    }
  }
}
