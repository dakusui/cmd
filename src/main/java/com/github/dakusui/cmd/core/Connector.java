package com.github.dakusui.cmd.core;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.core.Checks.greaterThan;
import static com.github.dakusui.cmd.core.Checks.requireArgument;
import static com.github.dakusui.cmd.core.ConcurrencyUtils.updateAndNotifyAll;
import static com.github.dakusui.cmd.core.ConcurrencyUtils.waitWhile;
import static java.lang.Integer.min;
import static java.util.Objects.requireNonNull;

public interface Connector<T> {

  void forEach(Consumer<T> consumer);

  abstract class Base<T> implements Connector<T> {

    private final ExecutorService threadPool;
    private final int numQueues;
    private final int eachQueueSize;

    public Base(ExecutorService threadPool, int numQueues, int eachQueueSize) {
      this.threadPool = threadPool;
      this.numQueues = numQueues;
      this.eachQueueSize = eachQueueSize;
    }

    @Override
    public void forEach(Consumer<T> consumer) {
      Connector.Base<T> connector = this;
      int numDownstreams = numQueues();
      ExecutorService threadPool = Executors.newFixedThreadPool(numDownstreams);
      AtomicInteger remaining = new AtomicInteger(numDownstreams);
      connector.streams().forEach(
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
    }

    int numQueues() {
      return this.numQueues;
    }

    int eachQueueSize() {
      return this.eachQueueSize;
    }

    ExecutorService threadPool() {
      return this.threadPool;
    }

    abstract public List<Stream<T>> streams();
  }

  abstract class BaseBuilder<T, C extends Connector<T>, B extends BaseBuilder<T, C, B>> {
    int numQueues;
    Supplier<ExecutorService> threadPoolFactory;
    int eachQueueSize;

    BaseBuilder() {
      this.threadPoolFactory(() -> Executors.newFixedThreadPool(this.numQueues + 1))
          .numQueues(min(Runtime.getRuntime().availableProcessors() - 1, 1))
          .eachQueueSize(100);
    }

    @SuppressWarnings("unchecked")
    public B threadPoolFactory(Supplier<ExecutorService> threadPoolFactory) {
      this.threadPoolFactory = requireNonNull(threadPoolFactory);
      return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B numQueues(int numQueues) {
      this.numQueues = requireArgument(numQueues, greaterThan(0));
      return (B) this;
    }

    @SuppressWarnings("unchecked")
    public B eachQueueSize(int queueSize) {
      this.eachQueueSize = requireArgument(queueSize, greaterThan(1));
      return (B) this;
    }

    abstract public C build();
  }


}
