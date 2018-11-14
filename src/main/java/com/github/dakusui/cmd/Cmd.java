package com.github.dakusui.cmd;

import com.github.dakusui.cmd.compat.CompatCmdImpl;
import com.github.dakusui.cmd.core.ProcessStreamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.core.StreamUtils.nop;
import static java.util.Objects.requireNonNull;

public interface Cmd {
  Logger LOGGER = LoggerFactory.getLogger(Cmd.class);

  static Builder builder() {
    return new Builder(
    ).transformInput(
        stream -> stream
    ).consumeStdout(
        nop()
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

  static Cmd cat() {
    return cmd("cat");
  }

  static Cmd cmd(String commandLine) {
    return local().command(commandLine).build();
  }

  static Cmd cmd(Shell shell, String commandLine) {
    return builder().with(shell).command(commandLine).build();
  }

  Cmd connect(Cmd cmd);

  /**
   * Returns a {@code Shell} object with which a command represented by this object
   * is run.
   */
  Shell getShell();

  Supplier<String> getCommand();

  /**
   * Sets a stream of strings from which this object read data. If you do not call
   * this method before the command represented by this object is executed, value
   * returned by {@code Stream.empty()} will be used as default.
   *
   * @param stdin A stream of strings from which this object read data.
   * @return This object
   */
  Cmd readFrom(Stream<String> stdin);

  /**
   * Sets a function that applies a given pipeline to a stream returned by {@code stream()}
   * method.
   *
   * @param pipeline A definition of the pipeline to be applied.
   * @return this object.
   */
  Cmd pipeline(Function<Stream<String>, Stream<String>> pipeline);

  <S extends Supplier<Stream<String>>> S stdin();

  /**
   * A stream returned by this method should be closed by {@code close()} when the
   * returned stream does not reach its end.
   *
   * @return A stream of strings.
   */
  default Stream<String> stream() {
    return this.getProcessStreamer().stream();
  }

  ProcessStreamer getProcessStreamer();

  State getState();

  void abort();

  void close();

  enum State {
    PREPARING,
    RUNNING,
    CLOSED
  }

  @SuppressWarnings("WeakerAccess")
  class Builder {
    private Shell                                    shell             = null;
    private Function<Stream<String>, Stream<String>> inputTransformer  = null;
    private Function<Stream<String>, Stream<String>> stdoutTransformer = null;
    private IntPredicate                             exitValueChecker  = null;
    private Supplier<String>                         commandSupplier;
    private Charset                                  charset           = Charset.defaultCharset();
    private Function<Stream<String>, Stream<String>> stderrTransformer;
    private Consumer<String>                         stdoutConsumer;
    private Consumer<String>                         stderrConsumer;
    private File                                     cwd;
    private Map<String, String>                      env               = Collections.emptyMap();

    public Builder with(Shell shell) {
      this.shell = requireNonNull(shell);
      return this;
    }

    public Builder cwd(File cwd) {
      this.cwd = cwd;
      return this;
    }

    public Builder env(Map<String, String> env) {
      List<String> illegalKeys = requireNonNull(env).keySet().stream()
          .filter(key -> key.contains("="))
          .collect(Collectors.toList());

      if (!illegalKeys.isEmpty()) {
        throw new IllegalArgumentException(illegalKeys.toString());
      }

      this.env = env;
      return this;
    }

    public Builder charset(Charset charset) {
      this.charset = requireNonNull(charset);
      return this;
    }

    public Builder command(String command) {
      requireNonNull(command);
      Supplier<String> commandSupplier = new Supplier<String>() {
        @Override
        public String get() {
          return command;
        }

        @Override
        public String toString() {
          return command;
        }
      };
      return this.command(commandSupplier);
    }

    public Builder command(Supplier<String> commandSupplier) {
      this.commandSupplier = requireNonNull(commandSupplier);
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
     * @param consumer A consumer applied for each string in stdout.
     */
    public Builder consumeStdout(Consumer<String> consumer) {
      this.stdoutConsumer = requireNonNull(consumer);
      return this;
    }

    /**
     * A consumer set by this method will be applied to each element in stderr stream
     * BEFORE transformer set by {@code }transformStderr} is applied.
     *
     * @param consumer A consumer applied for each string in stderr.
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
      return new CompatCmdImpl(this.shell, this.commandSupplier, this.cwd, this.env, this.exitValueChecker, this.inputTransformer, this.stdoutTransformer, this.stdoutConsumer, this.stderrTransformer, this.stderrConsumer, charset);
    }
  }

  class Impl implements Cmd {
    @Override
    public Cmd connect(Cmd cmd) {
      return null;
    }

    @Override
    public Shell getShell() {
      return null;
    }

    @Override
    public Supplier<String> getCommand() {
      return null;
    }

    @Override
    public Cmd readFrom(Stream<String> stdin) {
      return null;
    }

    @Override
    public Cmd pipeline(Function<Stream<String>, Stream<String>> pipeline) {
      return null;
    }

    @Override
    public <S extends Supplier<Stream<String>>> S stdin() {
      return null;
    }

    @Override
    public Stream<String> stream() {
      return null;
    }

    @Override
    public ProcessStreamer getProcessStreamer() {
      return null;
    }

    @Override
    public State getState() {
      return null;
    }

    @Override
    public void abort() {

    }

    @Override
    public void close() {

    }
  }
}
