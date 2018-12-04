package com.github.dakusui.cmd.pipeline;

import com.github.dakusui.cmd.core.stream.Merger;

import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.pipeline.Cmd.Type.PIPE;
import static com.github.dakusui.cmd.pipeline.Cmd.Type.SOURCE;
import static com.github.dakusui.cmd.utils.Checks.*;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

public interface Cmd {
  enum Type {
    SOURCE,
    PIPE,
    SINK,
    COMMAND
  }

  Type type();

  Cmd connect(Cmd... cmds);

  Stream<String> stream();

  Cmd map(Cmd cmd);

  Cmd reduce(Cmd cmd);

  static Cmd source(String command) {
    return new Impl(SOURCE);
  }

  static Cmd pipe(Stream<String> stdin, String command) {
    return new Impl(PIPE);
  }

  static Cmd sink(Stream<String> stdin, String command) {
    return new Impl(Type.SINK);
  }

  static Cmd cmd(String command) {
    return new Impl(Type.COMMAND);
  }

  class Impl implements Cmd {
    private final Type type;
    boolean alreadyStreamed = false;
    Cmd[] downstreams = null;

    Impl(Type type) {
      this.type = requireNonNull(type);
    }

    @Override
    public Type type() {
      return this.type;
    }

    @Override
    public Cmd connect(Cmd... cmds) {
      requireState(this, typeIs(SOURCE).or(typeIs(PIPE)));
      requireState(this.downstreams, isNull().negate());
      requireArgument(cmds.length, greaterThan(0));
      return this;
    }

    @Override
    public Cmd map(Cmd cmd) {
      return null;
    }

    @Override
    public Cmd reduce(Cmd cmd) {
      return null;
    }

    @Override
    public Stream<String> stream() {
      requireState(this, isAlreadyStreamed());
      alreadyStreamed = true;
      //noinspection unchecked
      return new Merger.Builder<>(asList((Stream<String>[]) downstreams))
          .build()
          .merge();
    }

    private Predicate<Impl> isAlreadyStreamed() {
      return new Predicate<Impl>() {
        @Override
        public boolean test(Impl impl) {
          return impl.alreadyStreamed;
        }

        @Override
        public String toString() {
          return "isAlreadyStreamed";
        }
      };
    }
  }
}
