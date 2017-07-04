package com.github.dakusui.cmd.core;

import com.github.dakusui.cmd.exceptions.Exceptions;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

public class CompatSelector<T> {
  private static final Object SENTINEL  = new Object() {
    @Override
    public String toString() {
      return "SENTINEL";
    }
  };
  private static final Object READ_NEXT = new Object() {
    @Override
    public String toString() {
      return "READ_NEXT";
    }
  };

  private final Map<Stream<T>, Consumer<Object>> streams;
  private final ExecutorService                  executorService;
  public final  BlockingQueue<Object>            queue;
  private       boolean                          closed;

  CompatSelector(Map<Stream<T>, Consumer<Object>> streams, BlockingQueue<Object> queue, ExecutorService executorService) {
    this.streams = new LinkedHashMap<Stream<T>, Consumer<Object>>() {{
      putAll(streams);
    }};
    this.executorService = executorService;
    this.queue = queue;
    this.closed = false;
    System.out.println("created:" + System.identityHashCode(queue));
  }

  public Stream<T> select() {
    System.out.println("select:" + System.identityHashCode(queue));
    this.drain(
        this.streams,
        this.executorService
    );
    return StreamSupport.stream(
        ((Iterable<T>) () -> new Iterator<T>() {
          Object next = READ_NEXT;

          @Override
          public boolean hasNext() {
            if (next == READ_NEXT) {
              next = takeFromQueue().orElse(SENTINEL);
            }
            return next != SENTINEL;
          }

          private Optional<Object> takeFromQueue() {
            synchronized (queue) {
              while (!(closed && queue.isEmpty())) {
                try {
                  return Optional.of(poll());
                } catch (InterruptedException ignored) {
                }
              }
            }
            return Optional.empty();
          }

          private Object poll() throws InterruptedException {
            Object ret = queue.poll(1, TimeUnit.MILLISECONDS);
            if (ret == null)
              throw new InterruptedException();
            return ret;
          }

          @SuppressWarnings("unchecked")
          @Override
          public T next() {
            if (next == SENTINEL)
              throw new NoSuchElementException();
            try {
              return (T) next;
            } finally {
              next = READ_NEXT;
            }
          }
        }).spliterator(),
        false
    );
  }

  public void close() {
    System.out.println("CompatSelector:start aborting");
    //    synchronized (queue) {
    System.out.println("CompatSelector:aborting");
    this.closed = true;
    //      this.queue.notifyAll();
    System.out.println("CompatSelector:aborted");
    //    }
  }

  public static class Builder<T> {
    private final Map<Stream<T>, Consumer<T>> streams;
    private final BlockingQueue<Object>       queue;
    private ExecutorService executorService = null;

    public Builder(int queueSize) {
      this.streams = new LinkedHashMap<>();
      this.queue = new ArrayBlockingQueue<>(queueSize);
    }

    public Builder<T> add(Stream<T> stream) {
      return add(stream, null);
    }

    public Builder<T> add(Stream<T> stream, Consumer<T> consumer) {
      this.streams.put(stream, consumer);
      return this;
    }

    public Builder<T> withExecutorService(ExecutorService executorService) {
      this.executorService = executorService;
      return this;
    }

    public CompatSelector<T> build() {
      Consumer<Object> defaultConsumer = new Consumer<Object>() {
        int numConsumedSentinels = 0;
        final int numStreamsForThisConsumer = (int) streams.values().stream().filter(Objects::isNull).count();

        @Override
        public synchronized void accept(Object t) {
          if (t != SENTINEL || (++numConsumedSentinels == numStreamsForThisConsumer))
            putInQueue(t);
        }

        private void putInQueue(Object t) {
          try {
            Builder.this.queue.put(t);
          } catch (InterruptedException e) {
            throw Exceptions.wrap(e);
          }
        }
      };
      return new CompatSelector<>(
          new LinkedHashMap<Stream<T>, Consumer<Object>>() {{
            Builder.this.streams.forEach(
                (stream, consumer) -> put(
                    stream,
                    consumer != null ?
                        (Consumer<Object>) o -> {
                          if (o != SENTINEL)
                            //noinspection unchecked
                            consumer.accept((T) o);
                        } :
                        defaultConsumer
                )
            );
          }},
          this.queue,
          Objects.requireNonNull(this.executorService)
      );
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private <T> void drain(Map<Stream<T>, Consumer<Object>> streams, ExecutorService executorService) {
    synchronized (queue) {
      if (!closed)
        streams.entrySet().stream(
        ).map(
            (Map.Entry<Stream<T>, Consumer<Object>> entry) ->
                (Runnable) () -> {
                  System.out.println("Runnable started:" + Thread.currentThread().getId());
                  for (Object o : Stream.concat(entry.getKey(), Stream.of(SENTINEL)).collect(toList())) {
                    System.out.println("Runnable running(1):" + Thread.currentThread().getId());
                    if (Thread.currentThread().isInterrupted()) {
                      System.out.println("Runnable being interrupted:*********************");
                      return;
                    }
                    System.out.println("Runnable running(2):" + Thread.currentThread().getId());
                    entry.getValue().accept(o);
                    System.out.println("Runnable running(3):" + Thread.currentThread().getId());
                  }
                  System.out.println("Runnable ended:" + Thread.currentThread().getId());
                }
        ).map(
            executorService::submit
        ).collect(
            toList()
        ); // Make sure submitted tasks are all completed.
    }
  }
}
