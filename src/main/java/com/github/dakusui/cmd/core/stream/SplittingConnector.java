package com.github.dakusui.cmd.core.stream;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.utils.ConcurrencyUtils.updateAndNotifyAll;
import static com.github.dakusui.cmd.utils.ConcurrencyUtils.waitWhile;
import static java.util.Objects.requireNonNull;

public interface SplittingConnector<T> extends Connector<T> {
  void forEach(Consumer<T> consumer);

  List<Stream<T>> split();

  abstract class BaseBuilder<T, C extends SplittingConnector<T>, B extends BaseBuilder<T, C, B>>
      extends Connector.BaseBuilder<T, C, B> {
    private final Stream<T> in;

    BaseBuilder(Stream<T> in) {
      this.in = requireNonNull(in);
    }

    Stream<T> in() {
      return this.in;
    }
  }

  abstract class Base<T> extends Connector.Base<T> implements SplittingConnector<T> {
    final Stream<T> in;

    Base(Supplier<ExecutorService> threadPoolFactory, int numQueues, int eachQueueSize, Stream<T> in) {
      super(threadPoolFactory, numQueues, eachQueueSize);
      this.in = requireNonNull(in).onClose(this::shutdownThreadPoolAndWaitForTermination);
    }

    @Override
    public void forEach(Consumer<T> consumer) {
      SplittingConnector<T> connector = this;
      int numDownstreams = numQueues();
      ExecutorService threadPool = Executors.newFixedThreadPool(numDownstreams);
      AtomicInteger remaining = new AtomicInteger(numDownstreams);
      connector.split().forEach(
          s -> threadPool.submit(
              () -> {
                s.forEach(consumer);
                synchronized (remaining) {
                  updateAndNotifyAll(remaining, AtomicInteger::decrementAndGet);
                }
              }
          )
      );
      synchronized (remaining) {
        waitWhile(remaining, c -> c.get() > 0);
      }
      shutdownThreadPoolAndWaitForTermination(threadPool);
    }
  }
}
