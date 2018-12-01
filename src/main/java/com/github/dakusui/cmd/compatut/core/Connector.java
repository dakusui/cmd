package com.github.dakusui.cmd.compatut.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.github.dakusui.cmd.compatut.core.Checks.greaterThan;
import static com.github.dakusui.cmd.compatut.core.Checks.requireArgument;
import static java.lang.Integer.max;
import static java.util.Objects.requireNonNull;

public interface Connector<T> {
  abstract class BaseBuilder<T, C extends Connector<T>, B extends BaseBuilder<T, C, B>> {
    int                       numQueues;
    Supplier<ExecutorService> threadPoolFactory;
    int                       eachQueueSize;

    BaseBuilder() {
      this.threadPoolFactory(() -> Executors.newFixedThreadPool(this.numQueues + 1, r -> new Thread(r, getClass().getCanonicalName())))
          .numQueues(max(Runtime.getRuntime().availableProcessors() - 1, 1))
          .eachQueueSize(1_000);
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

  abstract class Base<T> implements Connector<T> {
    private final ExecutorService threadPool;
    private final int             numQueues;
    private final int             eachQueueSize;

    Base(Supplier<ExecutorService> threadPoolFactory, int numQueues, int eachQueueSize) {
      this.threadPool = threadPoolFactory.get();
      this.numQueues = numQueues;
      this.eachQueueSize = eachQueueSize;
    }

    void shutdownThreadPoolAndWaitForTermination() {
      shutdownThreadPoolAndWaitForTermination(threadPool);
    }

    static void shutdownThreadPoolAndWaitForTermination(ExecutorService threadPool) {
      threadPool.shutdown();
      while (!threadPool.isTerminated()) {
        try {
          threadPool.awaitTermination(1, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        }
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
  }
}
