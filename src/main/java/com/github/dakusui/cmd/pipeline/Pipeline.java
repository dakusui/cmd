package com.github.dakusui.cmd.pipeline;

import com.github.dakusui.cmd.Shell;
import com.github.dakusui.cmd.core.Merger;
import com.github.dakusui.cmd.core.ProcessStreamer;
import com.github.dakusui.cmd.core.Tee;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * A wrapper of a process streamer's builder.
 */
public interface Pipeline {
  Pipeline stdin(Stream<String> stdin);

  Stream<String> stdin();

  Pipeline map(Function<String, String> mapper);

  Pipeline tee(Pipeline... pipelines);

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
    public Pipeline map(Function<String, String> mapper) {
      actions = actions.andThen(stream -> stream.map(mapper));
      return this;
    }

    @Override
    public Pipeline tee(Pipeline... pipelines) {
      actions = actions.andThen(
          createAction(pipelines.length, new Function<Stream<String>, Stream<String>>() {
            AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Stream<String> apply(Stream<String> stream) {
              return pipelines[counter.getAndIncrement()].stdin(stream).stream();
            }
          }));
      return this;
    }

    private Function<Stream<String>, Stream<String>> createAction(
        int numSplits,
        Function<Stream<String>, Stream<String>> streamMapper) {
      return stream -> new Merger.Builder<>(
          new Tee.Builder<>(stream)
              .numQueues(numSplits)
              .build().tee()
              .stream()
              .map(streamMapper)
              .collect(toList()))
          .build().merge();
    }

    @Override
    public Stream<String> stream() {
      return this.actions.apply(this.builder.stdin(stdin).build().stream());
    }
  }
}
