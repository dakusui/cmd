package com.github.dakusui.cmd.core;

import com.github.dakusui.cmd.StreamableQueue;
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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public enum IoUtils {
  ;

  /**
   * A sentinel, that lets consumers know end of a stream,  used in some classes
   * such as {@code Cmd} and {@code StreamableQueue} in this library.
   */
  public static final Object SENTINEL = new Object() {
    @Override
    public String toString() {
      return "SENTINEL";
    }
  };

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
  public static Consumer<String> toConsumer(OutputStream os, Charset charset) {
    try {
      PrintStream ps = new PrintStream(os, true, charset.displayName());
      return s -> {
        if (s != null) {
          ps.println(s);
        } else
          ps.close();
      };
    } catch (UnsupportedEncodingException e) {
      throw Exceptions.wrap(e);
    }
  }

  public static <T> Consumer<T> flowControlValve(Consumer<T> consumer, int queueSize) {
    return new StreamableQueue<T>(queueSize) {{
      new Thread(() -> Stream.concat(
          get(), Stream.of((T) null)
      ).forEach(consumer)).start();
    }};
  }

  /**
   * Returns a stream of strings that reads values from an {@code InputStream} {@code is}
   * using a {@code Charset} {@code charset}
   *
   * @param is      An input stream from which values are read by returned {@code Stream<String>}.
   * @param charset A charset with which values are read from {@code is}.
   */
  public static Stream<String> toStream(InputStream is, Charset charset) {
    return StreamSupport.stream(
        ((Iterable<String>) () -> toIterator(is, charset)).spliterator(),
        false
    ).filter(
        Objects::nonNull
    );
  }

  /**
   * Returns a consumer that does nothing.
   */
  public static <T> Consumer<T> nop() {
    return e -> {
    };
  }

  /**
   * An iterator returned by this method may return {@code null}, in case {@code next}
   * method is called after the input stream {@code is} is closed.
   *
   * @param is      An input stream from which returned iterator is created.
   * @param charset Charset used to decode data from {@code is}
   * @return An iterator that returns strings created from {@code id}
   */
  public static Iterator<String> toIterator(InputStream is, Charset charset) {
    return new Iterator<String>() {
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(
              is,
              charset
          )
      );
      IteratorState state = IteratorState.NOT_READ;
      String next;

      @Override
      public synchronized boolean hasNext() {
        readIfNotReadYet();
        return state != IteratorState.END;
      }

      @Override
      public synchronized String next() {
        if (state == IteratorState.END)
          throw new NoSuchElementException();
        readIfNotReadYet();
        try {
          return next;
        } finally {
          state = IteratorState.NOT_READ;
        }
      }

      private void readIfNotReadYet() {
        if (state == IteratorState.NOT_READ) {
          this.next = readLine(reader);
          state = this.next == null ?
              IteratorState.END :
              IteratorState.READ;
        }
      }

      private String readLine(BufferedReader reader) {
        try {
          return reader.readLine();
        } catch (IOException e) {
          return null;
        }
      }
    };
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

  private enum IteratorState {
    READ,
    NOT_READ,
    END
  }
}
