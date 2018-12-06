package com.github.dakusui.cmd.core.stream;

import com.github.dakusui.cmd.utils.StreamUtils;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.utils.StreamUtils.nop;
import static java.util.Objects.requireNonNull;

public interface Partitioner<T> extends SplittingConnector<T> {
  default List<Stream<T>> partition() {
    return split();
  }

  class Builder<T> extends SplittingConnector.BaseBuilder<T, Partitioner<T>, Builder<T>> {
    Function<T, Integer> partitioningFunction;

    public Builder(Stream<T> in) {
      super(in);
      this.partitioningFunction(Object::hashCode);
    }

    public Builder<T> partitioningFunction(Function<T, Integer> partitioningFunction) {
      this.partitioningFunction = requireNonNull(partitioningFunction);
      return this;
    }

    public Partitioner<T> build() {
      return new Partitioner.Impl<>(threadPoolFactory, numQueues, eachQueueSize, partitioningFunction, this.in());
    }
  }

  class Impl<T> extends SplittingConnector.Base<T> implements Partitioner<T> {
    private final Function<T, Integer> partitioningFunction;

    Impl(ThreadPoolFactory threadPoolFactory, int numQueues, int eachQueueSize, Function<T, Integer> partitioningFunction, Stream<T> in) {
      super(threadPoolFactory, numQueues, eachQueueSize, in);
      this.partitioningFunction = partitioningFunction;
    }

    @Override
    public List<Stream<T>> split() {
      return StreamUtils.partition(this.threadPool(), nop(), in, numQueues(), eachQueueSize(), partitioningFunction);
    }
  }
}
