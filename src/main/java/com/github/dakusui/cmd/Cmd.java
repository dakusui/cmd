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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public interface Cmd {
  Object SENTINEL = new Object();
  Logger LOGGER   = LoggerFactory.getLogger(Cmd.class);

  static Builder builder() {
    return new Builder(
    ).transformInput(
        stream -> stream
    ).consumeStdout(
        LOGGER::info
    ).transformStdout(
        stream -> stream
    ).consumeStderr(
        LOGGER::warn
    ).transformStderr(
        stream -> stream
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

  String getCommand();

  /**
   * Sets a stream of strings from which this object read data. If you do not call
   * this method before the command represented by this object is executed, value
   * returned by{@code Stream.empty()} will be used as default.
   *
   * @param stdin A supplier of a stream of strings from which this object read data.
   * @return This object
   */
  Cmd readFrom(Supplier<Stream<String>> stdin);

  Supplier<Stream<String>> stdin();

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
    private Function<Stream<String>, Stream<String>> stdoutTransformer = null;
    private IntPredicate                             exitValueChecker  = null;
    private String command;
    private Charset charset = Charset.defaultCharset();
    private Function<Stream<String>, Stream<String>> stderrTransformer;
    private Consumer<String>                         stdoutConsumer;
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

    public Builder transformStdout(Function<Stream<String>, Stream<String>> outputTransformer) {
      this.stdoutTransformer = requireNonNull(outputTransformer);
      return this;
    }

    public Builder transformStderr(Function<Stream<String>, Stream<String>> stderrTransformer) {
      this.stderrTransformer = requireNonNull(stderrTransformer);
      return this;
    }

    /**
     * A consumer set by this method will be applied to each element in stderr stream
     * BEFORE transformer set by {@code }transformStdout} is applied.
     *
     * @param consumer
     * @return
     */
    public Builder consumeStdout(Consumer<String> consumer) {
      this.stdoutConsumer = requireNonNull(consumer);
      return this;
    }

    /**
     * A consumer set by this method will be applied to each element in stderr stream
     * BEFORE transformer set by {@code }transformStderr} is applied.
     *
     * @param consumer
     * @return
     */
    public Builder consumeStderr(Consumer<String> consumer) {
      this.stderrConsumer = requireNonNull(consumer);
      return this;
    }

    public Builder checkExitValue(IntPredicate exitValueChecker) {
      this.exitValueChecker = requireNonNull(exitValueChecker);
      return this;
    }

    public Cmd build() {
      return new Impl(this.shell, this.command, this.exitValueChecker, this.inputTransformer, this.stdoutTransformer, this.stdoutConsumer, this.stderrTransformer, this.stderrConsumer, charset);
    }
  }

  class Impl implements Cmd {
    private final Shell                                    shell;
    private final String                                   command;
    private final Charset                                  charset;
    private final Function<Stream<String>, Stream<String>> inputTransformer;
    private final Function<Stream<String>, Stream<String>> stderrTransformer;
    private final Consumer<String>                         stderrConsumer;
    private final Function<Stream<String>, Stream<String>> stdoutTransformer;
    private final Consumer<String>                         stdoutConsumer;
    private final IntPredicate                             exitValueChecker;
    private final List<Cmd>                                downstreams;
    private       State                                    state;
    private       StreamableProcess                        process;
    private Supplier<Stream<String>> stdin = null;

    private Impl(Shell shell, String command, IntPredicate exitValueChecker, Function<Stream<String>, Stream<String>> inputTransformer, Function<Stream<String>, Stream<String>> stdoutTransformer, Consumer<String> stdoutConsumer, Function<Stream<String>, Stream<String>> stderrTransformer, Consumer<String> stderrConsumer, Charset charset) {
      this.exitValueChecker = requireNonNull(exitValueChecker);
      this.shell = requireNonNull(shell);
      this.command = requireNonNull(command);
      this.charset = requireNonNull(charset);
      this.inputTransformer = requireNonNull(inputTransformer);
      this.stdoutTransformer = requireNonNull(stdoutTransformer);
      this.stdoutConsumer = requireNonNull(stdoutConsumer);
      this.stderrTransformer = requireNonNull(stderrTransformer);
      this.stderrConsumer = requireNonNull(stderrConsumer);
      this.downstreams = new LinkedList<>();
      this.state = State.PREPARING;
    }

    @Override
    synchronized public Cmd readFrom(Supplier<Stream<String>> stdin) {
      requireNonNull(stdin);
      requireState(State.PREPARING);
      if (this.stdin != null)
        throw Exceptions.illegalState(this.stdin, "this.stdin==null");
      this.stdin = stdin;
      return this;
    }

    @Override
    synchronized public Supplier<Stream<String>> stdin() {
      if (this.stdin == null) {
        this.stdin = new StreamableQueue<>(100);
      }
      //noinspection unchecked
      return this.stdin;
    }

    @Override
    synchronized public Cmd pipeTo(Cmd... cmds) {
      requireState(State.PREPARING);
      this.downstreams.addAll(Arrays.asList(cmds));
      return this;
    }

    /**
     * It is not guaranteed that you get a {@code CommandExecutionException} on
     * an operation when a command fails because internal state can be changed by
     * independent thread and the returned stream can be closed by it. When this
     * happens an operation on the stream will throw a {@code RuntimeException} of
     * other types.
     *
     * @return A stream
     */
    @Override
    synchronized public Stream<String> stream() {
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
      );
      if (!downstreams.isEmpty()) {
        if (downstreams.size() > 1)
          ret = ret.parallel();
        for (Cmd each : downstreams) {
          Supplier<Stream<String>> stdin = each.stdin();
          if (stdin instanceof StreamableQueue)
            //noinspection unchecked
            ret = ret.peek((Consumer<? super String>) stdin);
        }
        Stream<String> up = ret;
        Selector.Builder<String> builder = new Selector.Builder<String>().add(
            Stream.concat(
                up,
                Stream.of((String) null
                )
            ).peek(
                (String s) -> {
                  if (s == null) {
                    downstreams.stream().map(each -> each.stdin()).filter(i -> i instanceof Consumer).map(i -> Consumer.class.cast(i)).forEach(c -> c.accept(null));
                    process.stdin().accept(null);
                  }
                }
            ),
            Selector.nop(),
            false
        );
        downstreams.forEach(each -> builder.add(each.stream(), Selector.nop(), true));
        ret = builder.build().stream();
      }
      return ret.peek(
          s -> LOGGER.trace("INFO:stream:{}:{}", this, s)
      );
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
    public String getCommand() {
      return this.command;
    }

    @Override
    public void abort() {
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
      LOGGER.debug("{}.alive={}", this, this.process.isAlive());
      downstreams.forEach((Cmd cmd) -> ((Cmd.Impl) cmd).dump(out));
    }


    synchronized private void close(boolean immediate) {
      LOGGER.debug("BEGIN:{};immediate={}", this, immediate);
      requireState(State.RUNNING, State.CLOSED);
      boolean abort = immediate || (!this.process.isAlive() && !this.exitValueChecker.test(this.process.exitValue()));
      LOGGER.trace("INFO:{};abort={}", this, abort);
      if (this.state == Cmd.State.CLOSED)
        return;
      try {
        LOGGER.trace("INFO:{};isAlive={}", this, this.process.isAlive());
        if (abort)
          this._abort();
        int exitValue;
        do {
          exitValue = this._waitFor();
        } while (this.process.isAlive());
        ////
        // By this point, the process should be finished.
        boolean failed = !this.exitValueChecker.test(exitValue);
        LOGGER.trace("INFO:{};failed={}", this, failed);
        if (abort || failed) {
          downstreams.stream(
          ).peek(each -> {
            Supplier<Stream<String>> stdin = each.stdin();
            if (stdin instanceof Consumer)
              //noinspection unchecked
              ((Consumer) each.stdin()).accept(null);
            each.abort();
          }).forEach(
              e -> {
              }
          );
        }

        if (failed) {
          throw new UnexpectedExitValueException(
              this.process.exitValue(),
              this.toString(),
              this.process.getPid()
          );
        }
      } finally {
        this.state = Cmd.State.CLOSED;
        LOGGER.debug("END:{};immediate={}", this, immediate);
      }
    }


    private void _abort() {
      LOGGER.debug("BEGIN:{}", this);
      this.process.destroy();
      LOGGER.debug("END:{}", this);
    }

    private int _waitFor() {
      LOGGER.debug("BEGIN:{}", this);
      try {
        return process.waitFor();
      } catch (InterruptedException e) {
        throw Exceptions.wrap(e, (Function<Throwable, RuntimeException>) throwable -> new CommandInterruptionException());
      } finally {
        LOGGER.debug("END:{}", this);
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
          this.inputTransformer.apply(
              this.stdin == null ?
                  Stream.empty() :
                  this.stdin.get()
          )
      ).configureStdout(
          this.stdoutConsumer,
          this.stdoutTransformer
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
