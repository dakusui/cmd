package com.github.dakusui.cmd.core;

import com.github.dakusui.cmd.exceptions.Exceptions;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.github.dakusui.cmd.core.Checks.greaterThan;
import static com.github.dakusui.cmd.core.Checks.requireArgument;
import static com.github.dakusui.cmd.core.ConcurrencyUtils.updateAndNotifyAll;
import static com.github.dakusui.cmd.core.ConcurrencyUtils.waitWhile;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public enum StreamUtils {
  ;

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
      Stream<String> in,
      int num,
      int queueSize,
      Function<T, Integer> partitioner) {
    return split(threadPool, in, num, queueSize,
        (blockingQueues, t) -> singletonList(blockingQueues.get(partitioner.apply(t) % num)));
  }

  @SuppressWarnings("unchecked")
  public static <T> List<Stream<T>> tee(
      ExecutorService threadPool,
      Stream<String> in,
      int num,
      int queueSize) {
    return split(threadPool, in, num, queueSize, (blockingQueues, t) -> blockingQueues);
  }

  @SuppressWarnings("unchecked")
  private static <T> List<Stream<T>> split(
      ExecutorService threadPool,
      Stream<String> in,
      int num,
      int queueSize,
      BiFunction<List<BlockingQueue<Object>>, T, List<BlockingQueue<Object>>> selector) {
    List<BlockingQueue<Object>> queues = IntStream.range(0, requireArgument(num, greaterThan(0)))
        .mapToObj(i -> new ArrayBlockingQueue<>(queueSize))
        .collect(toList());
    Object sentinel = createSentinel(0);
    AtomicBoolean initialized = new AtomicBoolean(false);
    threadPool.submit(
        () -> Stream.concat(in, Stream.of(sentinel))
            .forEach(e -> {
                  synchronized (initialized) {
                    updateAndNotifyAll(initialized, v -> v.set(true));
                  }
                  if (e == sentinel)
                    queues.forEach(q -> putElement(q, e));
                  else
                    selector.apply(queues, (T) e).forEach(q -> putElement(q, e));
                }
            )
    );
    synchronized (initialized) {
      waitWhile(initialized, AtomicBoolean::get);
    }
    return IntStream.range(0, num)
        .mapToObj(c -> StreamSupport.stream(
            ((Iterable<T>) () -> iteratorFinishingOnSentinel(
                e -> e == sentinel,
                blockingDataReader(queues.get(c)))).spliterator(),
            false))
        .collect(toList());
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
    for (Stream<T> a : streams) {
      Object sentinel = createSentinel(remainingStreams.get());
      sentinels.add(sentinel);
      threadPool.submit(
          () -> Stream.concat(a, Stream.of(sentinel))
              .forEach(e -> {
                synchronized (remainingStreams) {
                  updateAndNotifyAll(remainingStreams, AtomicInteger::decrementAndGet);
                }
                putElement(queue, e);
              }));
    }
    synchronized (remainingStreams) {
      waitWhile(remainingStreams, v -> v.get() > 0);
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
        return iteratorFinishingOnSentinel(isSentinel, readNext);
      }

    }.spliterator(), false);
  }

  private static <T> Iterator<T> iteratorFinishingOnSentinel(
      Predicate<Object> isSentinel, Supplier<Object> readNext) {
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
          this.next = readNext.get();
        return !isSentinel.test(this.next);
      }

      @SuppressWarnings("unchecked")
      @Override
      public T next() {
        if (this.next == this.invalid)
          this.next = readNext.get();
        if (isSentinel.test(this.next))
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

  private static Iterator<String> toIterator(BufferedReader br) {
    return new Iterator<String>() {
      String next = IoUtils.readLineFrom(br);

      @Override
      public boolean hasNext() {
        return this.next != null;
      }

      @Override
      public String next() {
        try {
          return this.next;
        } finally {
          this.next = IoUtils.readLineFrom(br);
        }
      }
    };
  }

  public static Stream<String> stream(InputStream is, Charset charset) {
    return toStream(IoUtils.bufferedReader(is, charset));
  }

  private static Stream<String> toStream(BufferedReader br) {
    return StreamSupport.stream(((Iterable<String>) () -> toIterator(br)).spliterator(), false);
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
