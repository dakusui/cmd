package com.github.dakusui.cmd.core;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface Pipeline {
  interface Stage extends AutoCloseable {
    int partition(String record);

    @Override
    void close();

  }

  interface Mapper
      extends Stage, Function<Stream<String>, Stream<String>> {
    @Override
    default int partition(String record) {
      return record.hashCode();
    }

    default List<Stream<String>> partition(Stream<String> in) {
      return StreamUtils.partition(
          Executors.newFixedThreadPool(3),
          in,
          3,
          100,
          this::partition
      );
    }
    default Mapper map(Mapper mapper) {
      return new Mapper() {
        @Override
        public Stream<String> apply(Stream<String> in) {
          return mapper.apply(Mapper.this.apply(in));
        }

        @Override
        public void close() {
          Mapper.this.close();
          mapper.close();
        }
      };
    }
  }

  interface Reducer
      extends Mapper {
    Mapper groupBy();
  }

  interface Source extends Stage, Supplier<Stream<String>> {
  }

  interface Sink extends Stage, Consumer<Stream<String>> {
    @Override
    default int partition(String record) {
      return record.hashCode();
    }
  }
}
