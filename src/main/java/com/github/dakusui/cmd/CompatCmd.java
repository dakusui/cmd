package com.github.dakusui.cmd;

import com.github.dakusui.cmd.core.StreamableProcess;
import com.github.dakusui.cmd.core.Tee;
import com.github.dakusui.cmd.exceptions.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

/*
 * Components under CompatCmd
 * - StreamableProcess
 *   - CompatSelector
 * - CompatCmd
 * - Tee.Connector
 * Terminations
 * - abort
 * - close
 */
public interface CompatCmd extends CmdObserver, CmdObservable {

  Stream<String> stream();

  int exitValue();

  int getPid();

  Shell getShell();

  StreamableProcess.Config getProcessConfig();

  void waitFor();

  void abort();

  default CompatCmd connect(String commandLine) {
    return connect(getShell(), commandLine);
  }

  default CompatCmd connect(Shell shell, String commandLine) {
    return connect(
        shell,
        (Stream<String> stdin) -> new StreamableProcess.Config.Builder(stdin).build(),
        commandLine
    );
  }

  /**
   * @param connector A function that creates a config object from stdin stream.
   */
  default CompatCmd connect(Shell shell, Function<Stream<String>, StreamableProcess.Config> connector, String commandLine) {
    CompatCmd ret = CompatCmd.cmd(
        shell,
        connector.apply(this.stream()),
        commandLine
    );
    addObserver(ret);
    return ret;
  }

  default CmdTee tee() {
    return new CmdTee(this, Tee.tee(this.stream()));
  }

  static Stream<String> stream(Shell shell, String commandLine) {
    return cmd(shell, commandLine).stream();
  }

  static Stream<String> stream(Shell shell, StreamableProcess.Config config, String commandLine) {
    return cmd(shell, config, commandLine).stream();
  }

  static CompatCmd cmd(Shell shell, String commandLine, Stream<String> stdin, Consumer<String> stdout, Consumer<String> stderr) {
    return cmd(
        shell,
        StreamableProcess.Config.builder(
            stdin
        ).configureStdout(
            Objects.requireNonNull(stdout)
        ).configureStderr(
            Objects.requireNonNull(stderr)
        ).build(),
        commandLine
    );
  }

  static CompatCmd cmd(Shell shell, String commandLine, Stream<String> stdin, Consumer<String> stdout) {
    return cmd(
        shell,
        commandLine,
        stdin,
        stdout,
        System.err::println
    );
  }

  static CompatCmd cmd(Shell shell, String commandLine, Stream<String> stdin) {
    return cmd(
        shell,
        commandLine,
        stdin,
        s -> {
        }
    );
  }

  static CompatCmd cmd(Shell shell, String commandLine) {
    return cmd(shell, commandLine, Stream.empty());
  }

  static CompatCmd cmd(Shell shell, StreamableProcess.Config config, String commandLine) {
    return new CompatCmd.Builder()
        .addAll(Collections.singletonList(commandLine))
        .withShell(shell)
        .configure(config)
        .build();
  }

  static CompatCmd.Builder local(String... commandLine) {
    return new CompatCmd.Builder().withShell(Shell.local()).addAll(asList(commandLine));
  }

  class Builder {
    Shell shell;
    List<String>             command = new LinkedList<>();
    StreamableProcess.Config config  = StreamableProcess.Config.create();

    public Builder add(String arg) {
      this.command.add(arg);
      return this;
    }

    public Builder withShell(Shell shell) {
      this.shell = shell;
      return this;
    }

    public Builder configure(StreamableProcess.Config config) {
      this.config = config;
      return this;
    }

    public CompatCmd build() {
      return new Impl(
          this.shell,
          String.join(" ", this.command),
          this.config
      );
    }

    public Builder addAll(List<String> command) {
      command.forEach(this::add);
      return this;
    }
  }

  class Impl implements CompatCmd {

    private List<CmdObserver> observers = new LinkedList<>();
    private Stream<String> output;

    @Override
    public void onFailure(CompatCmd upstream, RuntimeException upstreamException) {
      if (this.state != State.STARTED)
        throw Exceptions.illegalState(state, "!=State.STARTED");
      this.upstreamException = upstreamException;
      this.abort();
    }

    enum State {
      NOT_STARTED,
      STARTED,
      CLOSED
    }

    static final Object SENTINEL = new Object();

    private final Shell                    shell;
    private final String                   command;
    private       State                    state;
    private       StreamableProcess        process;
    private final StreamableProcess.Config processConfig;
    private       RuntimeException         upstreamException;

    Impl(Shell shell, String command, StreamableProcess.Config config) {
      this.shell = shell;
      this.command = command;
      this.processConfig = config;
      this.state = State.NOT_STARTED;
    }

    @Override
    public synchronized Stream<String> stream() {
      if (state != State.NOT_STARTED)
        throw Exceptions.illegalState(state, "==State.NOT_STARTED");
      this.run();
      try {
        return Stream.concat(
            output,
            Stream.of(SENTINEL)
        ).filter(
            o -> {
              if (o == SENTINEL) {
                close(false);
                ////
                // A sentinel shouldn't be passed to following stages.
                return false;
              }
              return true;
            }
        ).filter(
            o -> {
              if (!process.isAlive()) {
                close(false);
              }
              return true;
            }
        ).map(
            o -> (String) o
        ).onClose(
            () -> {
              close(true);
            }
        );
      } finally {
        this.state = State.STARTED;
      }
    }

    @Override
    public synchronized int exitValue() {
      if (this.state == State.NOT_STARTED)
        throw Exceptions.illegalState(state, "!=State.NOT_STARTED");
      return this.process.exitValue();
    }

    private void close(boolean abort) {
      if (this.state == State.CLOSED)
        return;
      if (this.state != State.STARTED)
        throw Exceptions.illegalState(state, "==State.STARTED");
      boolean succeeded = false;
      try {
        if (this.isAlive())
          if (abort)
            this._abort();
          else
            this._waitFor();
        succeeded = this.processConfig.exitValueChecker().test(this.exitValue());
        if (!succeeded) {
          throw new UnexpectedExitValueException(
              this.exitValue(),
              this.toString(),
              this.process.getPid()
          );
        }
      } finally {
        if (!succeeded || abort) {
          synchronized (this) {
            observers.forEach(cmd -> {
              try {
                cmd.onFailure(Impl.this, new RuntimeException("TODO"));
              } catch (RuntimeException ignored) {
              }
            });
          }
        } else {
          this.state = State.CLOSED;
        }
      }
      if (this.upstreamException != null)
        throw this.upstreamException;
    }

    @Override
    public synchronized int getPid() {
      if (this.state == State.NOT_STARTED)
        throw Exceptions.illegalState(this.state, "!=State.NOT_STARTED");
      if (this.state != State.CLOSED)
        this.close(false);
      return this.process.getPid();
    }

    @Override
    public Shell getShell() {
      return this.shell;
    }

    @Override
    public StreamableProcess.Config getProcessConfig() {
      return this.processConfig;
    }

    @Override
    public synchronized void addObserver(CmdObserver observer) {
      System.out.println("observer added:" + observer);
      this.observers.add(observer);
    }

    @Override
    public String toString() {
      return String.format("%s '%s'", this.getShell().format(), this.command);
    }

    private synchronized void run() {
      this.process = startProcess(this.shell, this.command, this.processConfig);
      this.output = this.process.getSelector().select();
    }

    boolean isAlive() {
      return this.process.isAlive();
    }

    @Override
    public void waitFor() {
      close(false);
    }

    @Override
    public void abort() {
      close(true);
    }

    private void _abort() {
      try {
        this.process.destroy();
      } finally {
        this.process.close();
      }
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

    private static StreamableProcess startProcess(Shell shell, String command, StreamableProcess.Config processConfig) {
      return new StreamableProcess(
          shell,
          command,
          processConfig
      );
    }

  }
}
