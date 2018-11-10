package com.github.dakusui.cmd.core;

import com.github.dakusui.cmd.exceptions.Exceptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.github.dakusui.cmd.core.Checks.requireArgument;

public enum IoUtils {
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
  public static Consumer<String> toStringConsumer(OutputStream os, Charset charset) {
    try {
      PrintStream ps = new PrintStream(os, true, charset.displayName());
      return s -> {
        if (s != null) {
          System.err.println("in toStringConsumer::" + s);
          ps.println(s);
        } else {
          ps.flush();
          ps.close();
        }
      };
    } catch (UnsupportedEncodingException e) {
      throw Exceptions.wrap(e);
    }
  }

  /**
   * Returns a consumer that does nothing.
   */
  public static <T> Consumer<T> nop() {
    return e -> {
    };
  }

  /**
   * Merges given streams possibly block into one keeping orders where elements
   * appear in original streams.
   *
   * @param queueSize The size of queue
   * @param streams   input streams
   * @param <T>       Type of elements that given streams contain.
   * @return merged stream
   */
  @SafeVarargs
  public static <T> Stream<T> merge(int queueSize, Stream<T>... streams) {
    BlockingQueue<Object> queue = new ArrayBlockingQueue<>(queueSize);
    Set<Object> sentinels = new HashSet<>();

    int i = 0;
    List<Thread> threads = new LinkedList<>();
    for (Stream<T> a : streams) {
      Object sentinel = createSentinel(i);
      sentinels.add(sentinel);
      threads.add(new Thread(
          () -> Stream.concat(a, Stream.of(sentinel))
              .forEach(e -> putElement(queue, e))));
      i++;
    }
    threads.forEach(Thread::start);

    return StreamSupport.stream(new Iterable<T>() {
      Iterator i = blockingQueueIterator(queue);

      @Override
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          private Object invalid = new Object();
          final Set<Object> remainingSentinels = new HashSet<>(sentinels);
          Object next = invalid;

          @Override
          public boolean hasNext() {
            if (this.next == this.invalid)
              this.next = readNext();
            return !isSentinel(this.next);
          }

          @SuppressWarnings("unchecked")
          @Override
          public T next() {
            if (this.next == this.invalid)
              this.next = readNext();
            if (isSentinel(this.next))
              throw new NoSuchElementException();
            try {
              return (T) requireArgument(this.next, v -> v instanceof String);
            } finally {
              this.next = this.invalid;
            }
          }

          Object readNext() {
            Object next = i.next();
            if (isSentinel(next)) {
              this.remainingSentinels.remove(next);
              if (this.remainingSentinels.isEmpty())
                return next;
              else
                return readNext();
            }
            return next;
          }

          private boolean isSentinel(Object next) {
            return sentinels.contains(next);
          }
        };
      }
    }.spliterator(), false);
  }

  private static Iterator blockingQueueIterator(BlockingQueue<Object> queue) {
    return new Iterator() {
      @Override
      public boolean hasNext() {
        return true;
      }

      @Override
      public Object next() {
        while (true) {
          try {
            return queue.take();
          } catch (InterruptedException ignored) {
          }
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
      System.err.println("in putElement:" + e);
      queue.put(e);
    } catch (InterruptedException ignored) {
    }
  }

  private static Iterator<String> toIterator(BufferedReader br) {
    return new Iterator<String>() {
      String next = readLineFrom(br);

      @Override
      public boolean hasNext() {
        return this.next != null;
      }

      @Override
      public String next() {
        try {
          return this.next;
        } finally {
          this.next = readLineFrom(br);
        }
      }
    };
  }

  private static String readLineFrom(BufferedReader br) {
    try {
      return br.readLine();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Stream<String> stream(InputStream is, Charset charset) {
    return toStream(bufferedReader(is, charset));
  }

  private static BufferedReader bufferedReader(InputStream is, Charset charset) {
    return new BufferedReader(new InputStreamReader(is, charset));
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
