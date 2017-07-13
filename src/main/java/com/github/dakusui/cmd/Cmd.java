package com.github.dakusui.cmd;

import com.github.dakusui.cmd.core.Selector;
import com.github.dakusui.cmd.core.StreamableProcess;
import com.github.dakusui.cmd.exceptions.CommandInterruptionException;
import com.github.dakusui.cmd.exceptions.Exceptions;
import com.github.dakusui.cmd.exceptions.UnexpectedExitValueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

public interface Cmd {
  Object SENTINEL = new Object();
  Logger LOGGER   = LoggerFactory.getLogger(Cmd.class);

  static Builder builder() {
    return new Builder(
    ).transformInput(
        stream -> stream
    ).transformOutput(
        stream -> stream
    ).transformStderr(
        stream -> stream
    ).consumeStderr(
        LOGGER::info
    ).checkExitValue(
        exitValue -> exitValue == 0
    ).charset(
        Charset.defaultCharset()
    );
  }

  static Builder builder(Shell shell) {
    return builder().with(shell);
  }

  static Builder local() {
    return builder(Shell.local());
  }

  static Cmd cmd(String commandLine) {
    return local().command(commandLine).build();
  }

  static Cmd cmd(Shell shell, String commandLine) {
    return builder().with(shell).command(commandLine).build();
  }

  Cmd pipeTo(Cmd... cmds);

  /**
   * Returns a {@code Shell} object with which a command represented by this object
   * is run.
   */
  Shell getShell();

  /**
   * Sets a stream of strings from which this object read data. If you do not call
   * this method before the command represented by this object is executed, value
   * returned by{@code Stream.empty()} will be used as default.
   *
   * @param stdin A supplier of a stream of strings from which this object read data.
   * @return This object
   */
  Cmd readFrom(Supplier<Stream<String>> stdin);

  /**
   * A stream returned by this method should be closed by {@code close()} when the
   * returned stream does not reach its end.
   *
   * @return A stream of strings.
   */
  Stream<String> stream();

  StreamableProcess getStreamableProcess();

  State getState();

  void abort();

  void close();

  enum State {
    PREPARING,
    RUNNING,
    CLOSED
  }

  class Builder {
    private Shell                                    shell             = null;
    private Function<Stream<String>, Stream<String>> inputTransformer  = null;
    private Function<Stream<String>, Stream<String>> outputTransformer = null;
    private IntPredicate                             exitValueChecker  = null;
    private String command;
    private Charset charset = Charset.defaultCharset();
    private Function<Stream<String>, Stream<String>> stderrTransformer;
    private Consumer<String>                         stderrConsumer;

    public Builder with(Shell shell) {
      this.shell = requireNonNull(shell);
      return this;
    }

    public Builder charset(Charset charset) {
      this.charset = requireNonNull(charset);
      return this;
    }

    public Builder command(String command) {
      this.command = requireNonNull(command);
      return this;
    }

    public Builder transformInput(Function<Stream<String>, Stream<String>> inputTransformer) {
      this.inputTransformer = requireNonNull(inputTransformer);
      return this;
    }

    public Builder transformOutput(Function<Stream<String>, Stream<String>> outputTransformer) {
      this.outputTransformer = requireNonNull(outputTransformer);
      return this;
    }

    public Builder transformStderr(Function<Stream<String>, Stream<String>> stderrTransformer) {
      this.stderrTransformer = requireNonNull(stderrTransformer);
      return this;
    }

    public Builder consumeStderr(Consumer<String> stderrConsumer) {
      this.stderrConsumer = requireNonNull(stderrConsumer);
      return this;
    }

    public Builder checkExitValue(IntPredicate exitValueChecker) {
      this.exitValueChecker = requireNonNull(exitValueChecker);
      return this;
    }

    public Cmd build() {
      return new Impl(this.shell, this.command, this.exitValueChecker, this.inputTransformer, this.outputTransformer, this.stderrTransformer, this.stderrConsumer, charset);
    }
  }

  class Impl implements Cmd {
    private final Function<Stream<String>, Stream<String>> inputTransformer;
    private final Function<Stream<String>, Stream<String>> outputTransformer;

    private final IntPredicate                             exitValueChecker;
    private final Charset                                  charset;
    private final Shell                                    shell;
    private final String                                   command;
    private final Function<Stream<String>, Stream<String>> stderrTransformer;
    private final Consumer<String>                         stderrConsumer;
    private final List<Cmd>                                downstreams;
    private       State                                    state;
    private       StreamableProcess                        process;
    private Supplier<Stream<String>> stdin = Stream::empty;

    private Impl(Shell shell, String command, IntPredicate exitValueChecker, Function<Stream<String>, Stream<String>> inputTransformer, Function<Stream<String>, Stream<String>> outputTransformer, Function<Stream<String>, Stream<String>> stderrTransformer, Consumer<String> stderrConsumer, Charset charset) {
      this.exitValueChecker = requireNonNull(exitValueChecker);
      this.shell = requireNonNull(shell);
      this.command = requireNonNull(command);
      this.charset = requireNonNull(charset);
      this.inputTransformer = requireNonNull(inputTransformer);
      this.outputTransformer = requireNonNull(outputTransformer);
      this.stderrTransformer = requireNonNull(stderrTransformer);
      this.stderrConsumer = requireNonNull(stderrConsumer);
      this.downstreams = new LinkedList<>();
      this.state = State.PREPARING;
    }

    @Override
    synchronized public Cmd readFrom(Supplier<Stream<String>> stdin) {
      requireNonNull(stdin);
      requireState(State.PREPARING);
      this.stdin = stdin;
      return this;
    }

    @Override
    synchronized public Cmd pipeTo(Cmd... cmds) {
      requireState(State.PREPARING);
      this.downstreams.addAll(Arrays.asList(cmds));
      return this;
    }

    @Override
    synchronized public Stream<String> stream() {
      System.out.println("START Cmd#stream:" + this);
      requireState(State.PREPARING);
      this.process = startProcess(this.shell, this.command, composeProcessConfig());
      this.state = State.RUNNING;
      Stream<String> ret = Stream.concat(
          process.getSelector().stream(),
          Stream.of(Cmd.SENTINEL)
      ).filter(
          o -> {
            if (o == Cmd.SENTINEL) {
              close();
              ////
              // A sentinel shouldn't be passed to following stages.
              return false;
            }
            return true;
          }
      ).peek(
          o -> {
            if (!process.isAlive())
              if (exitValueChecker.test(process.exitValue()))
                close();
              else {
                abort();
                throw new UnexpectedExitValueException(
                    this.process.exitValue(),
                    this.toString(),
                    this.process.getPid()
                );
              }
          }
      ).map(
          o -> (String) o
      ).peek(
          s -> {
          }
      );
      if (!downstreams.isEmpty()) {
        if (downstreams.size() > 1)
          ret = ret.parallel();
        List<StreamableQueue<String>> queues = new LinkedList<>();
        for (Cmd each : downstreams) {
          StreamableQueue<String> queue = new StreamableQueue<>(100);
          each.readFrom(queue);
          ret = ret.peek(queue);
          queues.add(queue);
        }
        Stream<String> up = ret;
        Selector.Builder<String> builder = new Selector.Builder<>();
        builder.add(
            Stream.concat(
                up,
                Stream.of((String) null
                )
            ).peek(
                s -> {
                  if (s == null)
                    for (StreamableQueue<String> each : queues)
                      each.accept(null);
                }
            ).filter(
                Objects::nonNull
            ),
            Selector.<String>nop().andThen(System.err::println),
            false
        );
        downstreams.forEach(each -> builder.add(each.stream(), Selector.nop(), true));
        ret = builder.build().stream();
      }
      System.out.println("END Cmd#stream:" + this);
      return ret;
    }

    @Override
    synchronized public StreamableProcess getStreamableProcess() {
      requireState(State.RUNNING, State.CLOSED);
      return requireNonNull(this.process);
    }

    @Override
    public State getState() {
      return this.state;
    }

    @Override
    public Shell getShell() {
      return this.shell;
    }

    @Override
    public void abort() {
      this.process.stdin().accept(null);
      close(true);
    }

    @Override
    public void close() {
      close(false);
    }

    @Override
    public String toString() {
      return String.format("%s '%s'", this.shell, this.command);
    }

    public void dump(PrintStream out) {
      out.println(String.format("%s.alive=%s", this, this.process.isAlive()));
      downstreams.forEach((Cmd cmd) -> ((Cmd.Impl) cmd).dump(out));
    }


    synchronized private void close(boolean abort) {
      System.out.println(String.format("closing(abort=%s,tid=%d):%s", abort, Thread.currentThread().getId(), this));
      requireState(State.RUNNING, State.CLOSED);
      if (this.state == Cmd.Impl.State.CLOSED)
        return;
      try {
        if (this.process.isAlive())
          if (abort)
            this._abort();
          else
            this._waitFor();
        if (abort)
          downstreams.stream().map(each -> {
            try {
              each.abort();
              return new RuntimeException("command aborted:" + each);
            } catch (RuntimeException e) {
              return e;
            }
          }).filter(
              Objects::nonNull
          ).collect(
              toList()
          ).stream(
          ).findFirst().ifPresent(e -> {
            throw e;
          });

        if (!this.exitValueChecker.test(this.process.exitValue())) {
          throw new UnexpectedExitValueException(
              this.process.exitValue(),
              this.toString(),
              this.process.getPid()
          );
        }
      } finally {
        this.state = Cmd.State.CLOSED;
      }
    }


    private void _abort() {
      System.out.println("_aborting");
      this.process.destroy();
      System.out.println("_aborted");
    }

    private void _waitFor() {
      try {
        process.waitFor();
      } catch (InterruptedException e) {
        throw Exceptions.wrap(e, (Function<Throwable, RuntimeException>) throwable -> new CommandInterruptionException());
      } finally {
        this.process.close();
      }
    }


    private void requireState(State... states) {
      for (State s : states)
        if (this.state == s)
          return;
      if (states.length == 1)
        throw Exceptions.illegalState(state, String.format("==%s", states[0]));
      throw Exceptions.illegalState(state, String.format("is one of %s", Arrays.asList(states)));
    }

    private StreamableProcess.Config composeProcessConfig() {
      return StreamableProcess.Config.builder(
      ).configureStdin(
          this.inputTransformer.apply(this.stdin.get())
      ).configureStdout(
          n -> {
          },
          this.outputTransformer
      ).configureStderr(
          this.stderrConsumer,
          this.stderrTransformer
      ).charset(
          this.charset
      ).build();
    }

    private static StreamableProcess startProcess(Shell shell, String command, StreamableProcess.Config processConfig) {
      return new StreamableProcess(
          shell,
          command,
          processConfig
      );
    }
  }

}
