package com.github.dakusui.cmd.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public interface Selector<T> {
  Stream<T> select();

  class Builder<T> {
    class Record<T> {
      private final T           data;
      private final Consumer<T> consumer;
      private final boolean     passToDownstream;

      Record(T data, Consumer<T> consumer, boolean passToDownstream) {
        this.data = data;
        this.consumer = consumer;
        this.passToDownstream = passToDownstream;
      }
    }

    private final Map<Stream<T>, Consumer<T>> consumers;
    private final Map<Stream<T>, Boolean>     toBePassed;


    public Builder() {
      this.consumers = new LinkedHashMap<>();
      toBePassed = new LinkedHashMap<>();
    }

    public Builder<T> add(Stream<T> stream, Consumer<T> consumer, boolean passToDownStream) {
      this.consumers.put(requireNonNull(stream), requireNonNull(consumer));
      this.toBePassed.put(stream, passToDownStream);
      return this;
    }

    public Selector<T> build() {
      return () -> consumers.keySet().stream(
      ).parallel(
      ).flatMap(
          stream -> stream.map(
              t -> new Record<>(t, consumers.get(stream), toBePassed.get(stream))
          )
      ).filter(
          r -> {
            r.consumer.accept(r.data);
            return r.passToDownstream;
          }
      ).map(
          tRecord -> tRecord.data
      ).onClose(
          () -> {
            System.out.println("selector closed");
            consumers.keySet().forEach(Stream::close);
          }
      );
    }
  }
}
