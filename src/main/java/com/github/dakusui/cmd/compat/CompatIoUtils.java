package com.github.dakusui.cmd.compat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.stream.Stream;

public enum CompatIoUtils {
  ;

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

  public static <T> Consumer<T> flowControlValve(Consumer<T> consumer, int queueSize) {
    return new StreamableQueue<T>(queueSize) {{
      new Thread(() -> Stream.concat(
          get(), Stream.of((T) null)
      ).forEach(consumer)).start();
    }};
  }

  private enum IteratorState {
    READ,
    NOT_READ,
    END
  }
}
