package com.github.dakusui.cmd.core;

import com.github.dakusui.cmd.exceptions.Exceptions;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
  public static Consumer<String> toConsumer(OutputStream os, Charset charset) {
    try {
      PrintStream ps = new PrintStream(os, true, charset.displayName());
      return s -> {
        if (s != null)
          ps.println(s);
        else
          ps.close();
      };
    } catch (UnsupportedEncodingException e) {
      throw Exceptions.wrap(e);
    }
  }

  /**
   * Returns a stream of strings that reads values from an {@code InputStream} {@code is}
   * using a {@code CharSet} {@code charset}
   *
   * @param is      An input stream from which values are read by returned {@code Stream<String>}.
   * @param charset A charset with which values are read from {@code is}.
   */
  public static Stream<String> toStream(InputStream is, Charset charset) {
    return StreamSupport.stream(
        ((Iterable<String>) () -> toIterator(is, charset)).spliterator(),
        false
    );
  }

  private enum IteratorState {
    READ,
    NOT_READ,
    END
  }

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
      public boolean hasNext() {
        readIfNotReadYet();
        return state != IteratorState.END;
      }

      @Override
      public String next() {
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
          throw Exceptions.wrap(e);
        }
      }
    };
  }
}
