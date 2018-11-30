package com.github.dakusui.cmd.core;

import com.github.dakusui.cmd.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.github.dakusui.cmd.core.Checks.greaterThan;
import static com.github.dakusui.cmd.core.Checks.requireArgument;
import static com.github.dakusui.cmd.core.ConcurrencyUtils.updateAndNotifyAll;
import static com.github.dakusui.cmd.core.ConcurrencyUtils.waitWhile;
import static java.lang.Math.abs;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public enum StreamUtils {
  ;
  private static final Logger LOGGER = LoggerFactory.getLogger(StreamUtils.class);

  /**
   * Returns a consumer which writes given string objects to an {@code OutputStream}
   * {@code os} using a {@code Charset} {@code charset}.
   * <p>
   * If {@code null} is given to the consumer returned by this method, the output
   * to {@code os} will be closed and the {@code null} will not be passed to it.
   *
   * @param os      OutputStream to which string objects given to returned consumer written.
   * @param charset A {@code Charset} object that specifies encoding by which
   */
  public static CloseableStringConsumer toCloseableStringConsumer(OutputStream os, Charset charset) {
    try {
      return CloseableStringConsumer.create(os, charset);
    } catch (UnsupportedEncodingException e) {
      throw Exceptions.wrap(e);
    }
  }

  public interface CloseableStringConsumer extends Consumer<String>, Closeable {
    @Override
    default void accept(String s) {
      if (s != null)
        this.writeLine(s);
      else
        this.close();
    }

    default void writeLine(String s) {
      this.printStream().println(s);
    }

    @Override
    default void close() {
      printStream().flush();
      printStream().close();
    }

    PrintStream printStream();

    static CloseableStringConsumer create(OutputStream os, Charset charset) throws UnsupportedEncodingException {
      PrintStream ps = new PrintStream(os, true, charset.name());
      return () -> ps;
    }
  }

  /**
   * Returns a consumer that does nothing.
   */
  public static <T> Consumer<T> nop() {
    return e -> {
    };
  }

  @SuppressWarnings("unchecked")
  public static <T> List<Stream<T>> partition(
      ExecutorService threadPool,
      Stream<T> in,
      int numQueues,
      int eachQueueSize,
      Function<T, Integer> partitioner) {
    return split(
        threadPool, in, numQueues, eachQueueSize,
        (blockingQueues, each) ->
            singletonList(blockingQueues.get(abs(partitioner.apply(each)) % numQueues)));
  }

  @SuppressWarnings("unchecked")
  public static <T> List<Stream<T>> tee(
      ExecutorService threadPool,
      Stream<T> in,
      int numQueues,
      int queueSize) {
    return split(threadPool, in, numQueues, queueSize, (blockingQueues, t) -> blockingQueues);
  }

  @SuppressWarnings("unchecked")
  private static <T> List<Stream<T>> split(
      ExecutorService threadPool,
      Stream<T> in,
      int numQueues,
      int eachQueueSize,
      BiFunction<List<BlockingQueue<Object>>, T, List<BlockingQueue<Object>>> selector) {
    List<BlockingQueue<Object>> queues = IntStream.range(0, requireArgument(numQueues, greaterThan(0)))
        .mapToObj(i -> new ArrayBlockingQueue<>(eachQueueSize))
        .collect(toList());

    Object sentinel = initializeSplit(threadPool, in, selector, queues);

    return IntStream.range(0, numQueues)
        .mapToObj(c -> StreamSupport.stream(
            ((Iterable<T>) () -> iteratorFinishingOnSentinel(
                e -> e == sentinel,
                blockingDataReader(queues.get(c)),
                in::close)).spliterator(),
            false)
        )
        .collect(toList());
  }

  private static <T> Object initializeSplit(
      ExecutorService threadPool,
      Stream<T> in,
      BiFunction<List<BlockingQueue<Object>>, T, List<BlockingQueue<Object>>> selector,
      List<BlockingQueue<Object>> queues) {
    Object sentinel = createSentinel(0);
    new Runnable() {
      final AtomicBoolean initialized = new AtomicBoolean(false);

      @SuppressWarnings("unchecked")
      public void run() {
        threadPool.submit(
            () -> Stream.concat(in, Stream.of(sentinel))
                .forEach(e -> {
                      if (e == sentinel)
                        queues.forEach(q -> {
                          initializeIfNecessaryAndNotifyAll();
                          putElement(q, e);
                        });
                      else
                        selector.apply(queues, (T) e).forEach(q -> {
                          initializeIfNecessaryAndNotifyAll();
                          putElement(q, e);
                        });
                    }
                )
        );
        synchronized (initialized) {
          waitWhile(initialized, AtomicBoolean::get);
        }
      }

      private void initializeIfNecessaryAndNotifyAll() {
        if (!initialized.get())
          synchronized (initialized) {
            updateAndNotifyAll(initialized, v -> v.set(true));
          }
      }
    }.run();
    return sentinel;
  }

  /**
   * Merges given streams possibly block into one keeping orders where elements
   * appear in original streams.
   *
   * @param threadPool A thread pool that gives threads by which data in {@code streams}
   *                   drained to the returned stream.
   * @param queueSize  The size of queue
   * @param streams    input streams
   * @param <T>        Type of elements that given streams contain.
   * @return merged stream
   */
  @SafeVarargs
  public static <T> Stream<T> merge(ExecutorService threadPool, int queueSize, Stream<T>... streams) {
    BlockingQueue<Object> queue = new ArrayBlockingQueue<>(queueSize);
    Set<Object> sentinels = new HashSet<>();

    AtomicInteger remainingStreams = new AtomicInteger(streams.length);
    for (Stream<T> each : streams) {
      Object sentinel = createSentinel(remainingStreams.get());
      sentinels.add(sentinel);
      threadPool.submit(
          () -> Stream.concat(each, Stream.of(sentinel))
              .forEach(e -> {
                synchronized (remainingStreams) {
                  updateAndNotifyAll(remainingStreams, AtomicInteger::decrementAndGet);
                }
                putElement(queue, e);
              }));
    }
    synchronized (remainingStreams) {
      boolean succeeded = false;
      try {
        waitWhile(remainingStreams, v -> v.get() > 0);
        succeeded = true;
      } finally {
        if (!succeeded)
          LOGGER.info("remainingStreams={}", remainingStreams);
      }
    }
    Supplier<Object> reader = blockingDataReader(queue);
    Set<Object> remainingSentinels = new HashSet<>(sentinels);
    Predicate<Object> isSentinel = sentinels::contains;
    return StreamSupport.stream(new Iterable<T>() {
      final Supplier<Object> readNext = () -> {
        Object next = reader.get();
        if (isSentinel.test(next)) {
          remainingSentinels.remove(next);
          if (remainingSentinels.isEmpty())
            return next;
          else
            return this.readNext.get();
        }
        return next;
      };

      @Override
      public Iterator<T> iterator() {
        return iteratorFinishingOnSentinel(
            isSentinel,
            readNext,
            () -> Arrays.stream(streams).forEach(Stream::close));
      }

    }.spliterator(), false);
  }

  private static <T> Iterator<T> iteratorFinishingOnSentinel(
      Predicate<Object> isSentinel, Supplier<Object> readNext, Runnable onFinish) {
    return new Iterator<T>() {
      /**
       * An object to let this iterator know that the {@code next} field
       * is not valid anymore and it needs to read the next value from the
       * source {@code i}.
       * This is different from  a sentinel.
       */
      private Object invalid = new Object();
      Object next = invalid;

      @Override
      public boolean hasNext() {
        if (this.next == this.invalid)
          this.next = readNext();
        return !isSentinel(this.next);
      }

      private Object readNext() {
        Object ret =  readNext.get();
        if (!isSentinel(ret))
          onFinish.run();
        return ret;
      }

      private boolean isSentinel(Object ret) {
        return isSentinel.test(ret);
      }

      @SuppressWarnings("unchecked")
      @Override
      public T next() {
        if (this.next == this.invalid)
          this.next = readNext();
        if (isSentinel(this.next))
          throw new NoSuchElementException();
        try {
          return (T) this.next;
        } finally {
          this.next = this.invalid;
        }
      }
    };
  }

  private static Supplier<Object> blockingDataReader(BlockingQueue<Object> queue) {
    return () -> {
      while (true) {
        try {
          return queue.take();
        } catch (InterruptedException ignored) {
        }
      }
    };
  }

  private static Object createSentinel(int i) {
    return new Object() {
      @Override
      public String toString() {
        return String.format("SENTINEL:%s", i);
      }
    };
  }

  private static void putElement(BlockingQueue<Object> queue, Object e) {
    try {
      queue.put(e);
    } catch (InterruptedException ignored) {
    }
  }

  public static Stream<String> stream(InputStream is, Charset charset) {
    return IoUtils.bufferedReader(is, charset).lines();
  }

  public interface RingBuffer<E> {
    void write(E elem);

    Stream<E> stream();

    static <E> RingBuffer<E> create(int size) {
      return new RingBuffer<E>() {
        int cur = 0;
        List<E> buffer = new ArrayList<>(size);

        @Override
        public void write(E elem) {
          this.buffer.add(cur++, elem);
          cur %= size;
        }

        @Override
        public synchronized Stream<E> stream() {
          return Stream.concat(
              this.buffer.subList(cur, this.buffer.size()).stream(),
              this.buffer.subList(0, cur).stream());
        }
      };
    }
  }
}
