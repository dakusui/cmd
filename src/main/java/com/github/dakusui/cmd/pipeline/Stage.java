package com.github.dakusui.cmd.pipeline;

import com.github.dakusui.cmd.Shell;
import com.github.dakusui.cmd.core.ProcessStreamer;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * A wrapper of a process streamer's builder.
 */
public interface Stage {
  Stage map(Function<String, String> mapper);

  Stage tee(Stage... stages);

  ProcessStreamer stream();

  interface Factory {
    default Stage cmd(String cmd) {
      return cmd(() -> cmd);
    }

    default Stage cmd(CommandLineComposer commandLineComposer) {
      return cmd(null, commandLineComposer);
    }

    default Stage cmd(Stream<String> stdin, String cmd) {
      requireNonNull(cmd);
      return cmd(stdin, () -> cmd);
    }

    default Stage cmd(Stream<String> stdin, CommandLineComposer commandLineComposer) {
      return new Impl(stdin, shell(), requireNonNull(commandLineComposer));
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

  class Impl implements Stage {
    final ProcessStreamer.Builder builder;
    final Stream<String>          stdin;
    List<Stage> downstreams = new LinkedList<>();

    Impl(Stream<String> stdin, Shell shell, CommandLineComposer commandLineComposer) {
      this.stdin = stdin;
      this.builder = new ProcessStreamer.Builder(shell, commandLineComposer.compose());
    }

    @Override
    public Stage map(Function<String, String> mapper) {
      return null;
    }

    @Override
    public Stage tee(Stage... stages) {
      return this;
    }

    @Override
    public ProcessStreamer stream() {
      return this.builder.stdin(stdin).build();
    }
  }
}
