package com.github.dakusui.cmd.core.stream;

import com.github.dakusui.cmd.utils.ConcurrencyUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static com.github.dakusui.cmd.utils.Checks.greaterThan;
import static com.github.dakusui.cmd.utils.Checks.requireArgument;
import static java.lang.Integer.max;
import static java.util.Objects.requireNonNull;

public interface Connector<T> {
  interface ThreadPoolFactory {
    ExecutorService createThreadPool();

    Consumer<ExecutorService> createCloser();

    static ThreadPoolFactory create(int numThreads) {
      return new ThreadPoolFactory() {
        @Override
        public ExecutorService createThreadPool() {
          return Executors.newFixedThreadPool(numThreads);
        }

        @Override
        public Consumer<ExecutorService> createCloser() {
          return ConcurrencyUtils::shutdownThreadPoolAndAwaitTermination;
        }
      };
    }
  }

  abstract class BaseBuilder<T, C extends Connector<T>, B extends BaseBuilder<T, C, B>> {
    int               numQueues;
    ThreadPoolFactory threadPoolFactory;
    int               eachQueueSize;

    BaseBuilder() {
      this.threadPoolFactory(ThreadPoolFactory.create(this.numQueues + 1))
          .numQueues(max(Runtime.getRuntime().availableProcessors() - 1, 1))
          .eachQueueSize(1_000);
    }

    @SuppressWarnings("unchecked")
    public B threadPoolFactory(ThreadPoolFactory threadPoolFactory) {
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
    private final ExecutorService           threadPool;
    private final int                       numQueues;
    private final int                       eachQueueSize;
    private final Consumer<ExecutorService> threadPoolCloser;

    Base(SplittingConnector.ThreadPoolFactory threadPoolFactory, int numQueues, int eachQueueSize) {
      this.threadPool = threadPoolFactory.createThreadPool();
      this.threadPoolCloser = threadPoolFactory.createCloser();
      this.numQueues = numQueues;
      this.eachQueueSize = eachQueueSize;
    }

    void shutdownThreadPoolAndWaitForTermination() {
      ConcurrencyUtils.shutdownThreadPoolAndAwaitTermination(threadPool);
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

    Consumer<ExecutorService> threadPoolCloser() {
      return this.threadPoolCloser;
    }
  }
}
