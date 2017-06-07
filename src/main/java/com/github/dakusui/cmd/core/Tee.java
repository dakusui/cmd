package com.github.dakusui.cmd.core;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

public class Tee<T> extends Thread {
  private static final Object SENTINEL = new Object() {
    @Override
    public String toString() {
      return "SENTINEL";
    }
  };
  private final Stream<T>           in;
  private final List<Queue<Object>> queues;
  private final List<Stream<T>>     streams;

  public static class Builder<T> {
    private final Stream<T> in;
    private int     queueSize      = 8192;
    private Integer numDownStreams = 1;

    public Builder(Stream<T> in) {
      this.in = Objects.requireNonNull(in);
    }

    public Builder<T> setQueueSize(int queueSize) {
      this.queueSize = check(queueSize, v -> v > 0, IllegalArgumentException::new);
      return this;
    }

    public Builder<T> setNumberOfStreams(int numDownStreams) {
      this.numDownStreams = check(numDownStreams, v -> v > 0, IllegalArgumentException::new);
      return this;
    }

    public List<Stream<T>> connect() {
      Tee<T> tee = new Tee<>(this.in, numDownStreams, this.queueSize);
      tee.start();
      return Collections.unmodifiableList(tee.streams);
    }

    public void connect(List<Consumer<Stream<T>>> consumers) {
      this.setNumberOfStreams(numDownStreams);
      Tee<T> tee = new Tee<>(this.in, numDownStreams, this.queueSize);
      tee.start();
      AtomicInteger i = new AtomicInteger(0);
      tee.streams.parallelStream()
          .forEach(
              consumers.get(i.getAndIncrement())
          );
    }
  }

  private Tee(Stream<T> in, int numDownStreams, int queueSize) {
    this.in = Objects.requireNonNull(in);
    queues = new LinkedList<>();
    for (int i = 0; i < numDownStreams; i++) {
      queues.add(new ArrayBlockingQueue<>(queueSize));
    }
    streams = createDownStreams();
  }

  @Override
  public void run() {
    List<Queue<Object>> pendings = new LinkedList<>();
    Stream.concat(in, Stream.of(SENTINEL))
        .forEach((Object t) -> {
          pendings.addAll(queues);
          synchronized (queues) {
            while (!pendings.isEmpty()) {
              queues.stream()
                  .filter(pendings::contains)
                  .filter(queue -> queue.offer(t))
                  .forEach(pendings::remove);
              queues.notifyAll();
              try {
                queues.wait();
              } catch (InterruptedException ignored) {
              }
            }
          }
        });
  }

  private List<Stream<T>> createDownStreams() {
    return queues.stream()
        .map((Queue<Object> queue) -> (Iterable<T>) () -> new Iterator<T>() {
              Object next;

              @Override
              public boolean hasNext() {
                if (next == null)
                  getNext();
                return next != SENTINEL;
              }

              @SuppressWarnings("unchecked")
              @Override
              public T next() {
                if (next == null)
                  getNext();
                T ret = check((T) next, v -> v != SENTINEL, NoSuchElementException::new);
                if (next != SENTINEL)
                  next = null;
                return ret;
              }

              private void getNext() {
                synchronized (queues) {
                  while ((next = pollQueue()) == null) {
                    try {
                      queues.wait();
                    } catch (InterruptedException ignored) {
                    }
                  }
                }
              }

              private Object pollQueue() {
                try {
                  return next = queue.poll();
                } finally {
                  queues.notifyAll();
                }
              }
            }
        ).map(
            (Iterable<T> iterable) -> StreamSupport.stream(iterable.spliterator(), false)
        ).collect(
            toList()
        );
  }

  private static <T, E extends Throwable> T check(T value, Predicate<T> check, Supplier<E> exceptionSupplier) throws E {
    if (check.test(value))
      return value;
    throw exceptionSupplier.get();
  }
}
