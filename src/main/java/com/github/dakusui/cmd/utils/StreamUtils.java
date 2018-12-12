package com.github.dakusui.cmd.utils;

import com.github.dakusui.cmd.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import static com.github.dakusui.cmd.utils.Checks.greaterThan;
import static com.github.dakusui.cmd.utils.Checks.requireArgument;
import static com.github.dakusui.cmd.utils.ConcurrencyUtils.updateAndNotifyAll;
import static com.github.dakusui.cmd.utils.ConcurrencyUtils.waitWhile;
import static java.lang.Math.abs;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * = A Stream Utility class
 * This is a stream utility class.
 */
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
   * @return A closeable strinmg consumer object.
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
   *
   * @param <T> Type of values to be consumed by returned object.
   * @return A consumer that doesn't do anything.
   */
  public static <T> Consumer<T> nop() {
    return e -> {
    };
  }

  public static <T> Stream<T> closeOnFinish(Stream<T> in) {
    return onFinish(in, Stream::close).onClose(in::close);
  }

  @SuppressWarnings("unchecked")
  public static <T> Stream<T> onFinish(Stream<T> in, Consumer<Stream<T>> action) {
    Object sentinel = new Object() {
      @Override
      public String toString() {
        return "(sentinel)";
      }
    };
    return Stream.concat(requireNonNull(in), Stream.of(sentinel))
        .filter(o -> {
          if (o != sentinel)
            return true;
          else {
            action.accept(in);
            return false;
          }
        })
        .map(each -> (T) each);
  }

  public static <T> List<Stream<T>> partition(
      ExecutorService threadPool,
      Consumer<ExecutorService> threadPoolCloser,
      Stream<T> in,
      int numQueues,
      int eachQueueSize,
      Function<T, Integer> partitioner) {
    return split(
        threadPool, threadPoolCloser, in, numQueues, eachQueueSize,
        (blockingQueues, each) ->
            singletonList(blockingQueues.get(abs(partitioner.apply(each)) % numQueues)));
  }

  public static <T> List<Stream<T>> tee(
      ExecutorService threadPool,
      Consumer<ExecutorService> threadPoolCloser,
      Stream<T> in,
      int numQueues,
      int queueSize) {
    return split(threadPool, threadPoolCloser, in, numQueues, queueSize, (blockingQueues, t) -> blockingQueues);
  }

  private static <T> List<Stream<T>> split(
      ExecutorService threadPool,
      Consumer<ExecutorService> threadPoolCloser,
      Stream<T> in,
      int numQueues,
      int eachQueueSize,
      BiFunction<List<BlockingQueue<Object>>, T, List<BlockingQueue<Object>>> selector) {
    requireArgument(numQueues, greaterThan(0));
    if (numQueues == 1)
      return singletonList(in);
    List<BlockingQueue<Object>> queues = IntStream.range(0, numQueues)
        .mapToObj(i -> new ArrayBlockingQueue<>(eachQueueSize))
        .collect(toList());

    Object sentinel = initializeSplit(threadPool, threadPoolCloser, in, selector, queues);

    return IntStream.range(0, numQueues)
        .mapToObj(
            c -> StreamSupport.stream(
                ((Iterable<T>) () -> iteratorFinishingOnSentinel(
                    e -> e == sentinel,
                    blockingDataReader(queues.get(c)))).spliterator(),
                false))
        .collect(toList());
  }

  private static <T> Object initializeSplit(
      ExecutorService threadPool,
      Consumer<ExecutorService> threadPoolCloser,
      Stream<T> in,
      BiFunction<List<BlockingQueue<Object>>, T, List<BlockingQueue<Object>>> selector,
      List<BlockingQueue<Object>> queues) {
    class TaskSubmitter implements Runnable {
      private final AtomicBoolean initialized = new AtomicBoolean(false);
      private final Object        sentinel;

      private TaskSubmitter(Object sentinel) {
        this.sentinel = sentinel;
      }

      @SuppressWarnings("unchecked")
      public void run() {
        threadPool.submit(
            () -> StreamUtils.closeOnFinish(
                Stream.concat(in, Stream.of(sentinel))
                    .onClose(() -> threadPoolCloser.accept(threadPool)))
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
          waitWhile(initialized, i -> !i.get());
        }
      }

      private void initializeIfNecessaryAndNotifyAll() {
        synchronized (initialized) {
          if (!initialized.get())
            updateAndNotifyAll(initialized, v -> v.set(true));
        }
      }
    }
    Object sentinel = createSentinel(0);
    new TaskSubmitter(sentinel).run();
    return sentinel;
  }

  /**
   * = Merging function
   * Merges given streams possibly block into one keeping orders where elements
   * appear in original streams.
   *
   * [ditaa]
   * ----
   *
   *    +-----+
   *    |Queue|
   *    +-----+
   *
   * ----
   *
   * @param threadPool A thread pool that gives threads by which data in {@code streams}
   *                   drained to the returned stream.
   * @param queueSize  The size of queue
   * @param streams    input streams
   * @param <T>        Type of elements that given streams contain.
   * @return merged stream
   */
  @SafeVarargs
  public static <T> Stream<T> merge(
      ExecutorService threadPool,
      Consumer<ExecutorService> threadPoolCloser,
      int queueSize,
      Stream<T>... streams) {
    if (streams.length == 0)
      return Stream.empty();
    if (streams.length == 1)
      return streams[0];
    BlockingQueue<Object> queue = new ArrayBlockingQueue<>(queueSize);
    Set<Object> sentinels = new HashSet<>();

    AtomicInteger remainingStreams = new AtomicInteger(streams.length);
    int i = 0;
    for (Stream<T> eachStream : streams) {
      Object sentinel = createSentinel(i++);
      sentinels.add(sentinel);
      LOGGER.trace("Submitting task for:{}", sentinel);
      Consumer<Object> action = new Consumer<Object>() {
        boolean started = false;

        @Override
        public void accept(Object elementOrSentinel) {
          LOGGER.trace("{}, is trying to put:{}", sentinel, elementOrSentinel);
          if (!started) {
            LOGGER.trace("task:stream:sentinel={} starting", sentinel);
            synchronized (remainingStreams) {
              updateAndNotifyAll(remainingStreams, AtomicInteger::decrementAndGet);
              started = true;
            }
          }
          putElement(queue, elementOrSentinel);
        }
      };
      threadPool.execute(
          () -> Stream.concat(eachStream, Stream.of(sentinel)).forEach(action)
      );
      LOGGER.trace("Submitted task for:{}", eachStream);
    }
    synchronized (remainingStreams) {
      boolean succeeded = false;
      try {
        waitWhile(remainingStreams, v -> v.get() > 0);
        succeeded = true;
      } finally {
        if (!succeeded)
          LOGGER.error("remainingStreams={}", remainingStreams);
      }
    }
    Supplier<Object> reader = blockingDataReader(queue);
    Set<Object> remainingSentinels = new HashSet<>(sentinels);
    Predicate<Object> isSentinel = sentinels::contains;
    return StreamSupport.stream(new Iterable<T>() {
      final Supplier<Object> readNext = () -> {
        Object nextElementOrSentinel = reader.get();
        if (isSentinel.test(nextElementOrSentinel)) {
          remainingSentinels.remove(nextElementOrSentinel);
          if (remainingSentinels.isEmpty())
            return nextElementOrSentinel;
          else
            return this.readNext.get();
        }
        return nextElementOrSentinel;
      };

      @Override
      public Iterator<T> iterator() {
        return iteratorFinishingOnSentinel(isSentinel, readNext);
      }

    }.spliterator(), false).onClose(() -> threadPoolCloser.accept(threadPool));
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
