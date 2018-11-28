package com.github.dakusui.cmd.pipeline;

import com.github.dakusui.cmd.Shell;
import com.github.dakusui.cmd.core.Merger;
import com.github.dakusui.cmd.core.Partitioner;
import com.github.dakusui.cmd.core.ProcessStreamer;
import com.github.dakusui.cmd.core.Tee;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.BaseStream;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * A wrapper of a process streamer's builder.
 */
public interface Pipeline {
  Pipeline stdin(Stream<String> stdin);

  Stream<String> stdin();

  default Pipeline map(int numPartitions, Pipeline mapper) {
    return this.mapStream(
        numPartitions,
        in -> mapper.stdin(in).stream()
    );
  }

  Pipeline mapStream(int numPartitions, Function<Stream<String>, Stream<String>> mapper);

  default Pipeline map(int numPartitions, Function<String, String> mapper) {
    return mapStream(numPartitions, in -> in.map(mapper));
  }

  /**
   * Connects {@code pipelines} to downstream side of this pipeline.
   * The {@code Stream<String>} returned by {@link Pipeline#stream()} method will be
   * {@code tee}'ed to them by using {@link Tee} class.
   * However, if an element in {@code pielines} returns non-{@code null} stream when
   * {@code stream()} method is called, the stream used for the element's {@code stdio}.
   *
   * @param pipelines downstream pipelines.
   * @return This pipeline.
   */
  Pipeline tee(Pipeline... pipelines);

  /**
   * Returns a {@code Stream<String>} object. The stream must be closed by a user.
   *
   * @return A stream
   */
  Stream<String> stream();

  interface Factory {
    default Pipeline cmd(String cmd) {
      requireNonNull(cmd);
      return cmd(() -> cmd);
    }

    default Pipeline cmd(CommandLineComposer commandLineComposer) {
      return new Impl(shell(), requireNonNull(commandLineComposer));
    }

    default Shell shell() {
      return Shell.local();
    }
  }

  @FunctionalInterface
  interface CommandLineComposer extends Supplier<String> {
    default String compose() {
      return requireNonNull(this.get());
    }
  }

  class Impl implements Pipeline {
    final ProcessStreamer.Builder builder;
    Stream<String>                           stdin;
    /**
     * A nested function that represents a sequence of actions performed on the stream
     * of the {@code ProcessStreamer} built by this object.
     */
    Function<Stream<String>, Stream<String>> actions = Function.identity();

    Impl(Shell shell, CommandLineComposer commandLineComposer) {
      this.builder = new ProcessStreamer.Builder(shell, commandLineComposer.compose());
    }

    public Pipeline stdin(Stream<String> stdin) {
      this.stdin = stdin;
      return this;
    }

    public Stream<String> stdin() {
      return this.stdin;
    }

    @Override
    public Pipeline mapStream(int numPartitions, Function<Stream<String>, Stream<String>> mapper) {
      if (numPartitions == 1)
        actions = actions.andThen(mapper);
      else
        actions = actions.andThen(
            partition(
                numPartitions,
                mapper,
                Object::hashCode));
      return this;
    }

    @Override
    public Pipeline tee(Pipeline... pipelines) {
      actions = actions.andThen(
          tee(pipelines.length, new Function<Stream<String>, Stream<String>>() {
            AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Stream<String> apply(Stream<String> stream) {
              Pipeline pipeline = pipelines[counter.getAndIncrement()];
              return pipeline.stdin() == null ?
                  pipeline.stdin(stream).stream() :
                  pipeline.stream();
            }
          }));
      return this;
    }

    @Override
    public Stream<String> stream() {
      final Stream<String> up;
      return this.actions.apply(up = this.builder.stdin(stdin).build().stream())
          .onClose(up::close);
    }

    private Function<Stream<String>, Stream<String>> tee(
        int numSplits,
        Function<Stream<String>, Stream<String>> streamMapper) {
      return stream -> {
        List<Stream<String>> splits;
        return new Merger.Builder<>(
            (splits = new Tee.Builder<>(stream)
                .numQueues(numSplits)
                .build().tee()
                .stream()
                .map(streamMapper)
                .collect(toList())))
            .build()
            .merge()
            .onClose(() -> splits.forEach(BaseStream::close));
      };
    }

    private Function<Stream<String>, Stream<String>> partition(
        int numPartitions,
        Function<Stream<String>, Stream<String>> streamMapper,
        Function<String, Integer> partitioningFunction
    ) {
      return stream -> {
        List<Stream<String>> splits;
        return new Merger.Builder<>(
            (splits = new Partitioner.Builder<>(stream)
                .numQueues(numPartitions)
                .partitioningFunction(partitioningFunction)
                .build()
                .partition()
                .stream()
                .map(streamMapper)
                .collect(toList())))
            .build()
            .merge()
            .onClose(() -> splits.forEach(BaseStream::close));
      };
    }

  }
}
