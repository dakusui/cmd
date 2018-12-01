package com.github.dakusui.cmd.compat;

import com.github.dakusui.cmd.Cmd;
import com.github.dakusui.cmd.Shell;
import com.github.dakusui.cmd.compatut.core.StreamUtils;
import com.github.dakusui.cmd.compatut.core.ProcessStreamer;
import com.github.dakusui.cmd.exceptions.Exceptions;
import com.github.dakusui.cmd.exceptions.UnexpectedExitValueException;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.compatut.core.StreamUtils.nop;
import static java.util.Objects.requireNonNull;

public class CompatCmdImpl implements Cmd {
  private final Shell                                    shell;
  private final Supplier<String>                         command;
  private final Charset                                  charset;
  private       Function<Stream<String>, Stream<String>> inputTransformer;
  private final Function<Stream<String>, Stream<String>> stderrTransformer;
  private final Consumer<String>                         stderrConsumer;
  private final Function<Stream<String>, Stream<String>> stdoutTransformer;
  private final Consumer<String>                         stdoutConsumer;
  private final IntPredicate                             exitValueChecker;
  private final List<Cmd>                                downstreams;
  private       State                                    state;
  private       ProcessStreamer                          process;
  private       Supplier<Stream<String>>                 stdin = null;
  private final File                                     cwd;
  private final Map<String, String>                      env;

  public CompatCmdImpl(
      Shell shell,
      Supplier<String> commandSupplier,
      File cwd,
      Map<String, String> env,
      IntPredicate exitValueChecker,
      Function<Stream<String>, Stream<String>> inputTransformer,
      Function<Stream<String>, Stream<String>> stdoutTransformer,
      Consumer<String> stdoutConsumer,
      Function<Stream<String>, Stream<String>> stderrTransformer,
      Consumer<String> stderrConsumer,
      Charset charset
  ) {
    this.exitValueChecker = requireNonNull(exitValueChecker);
    this.shell = requireNonNull(shell);
    this.command = requireNonNull(commandSupplier);
    this.cwd = cwd;
    this.env = env;
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
  synchronized public Cmd readFrom(Stream<String> stdin) {
    requireNonNull(stdin);
    requireState(State.PREPARING);
    if (this.stdin != null)
      throw Exceptions.illegalState(this.stdin, "this.stdin==null");
    this.stdin = () -> stdin;
    return this;
  }

  @Override
  public Cmd pipeline(Function<Stream<String>, Stream<String>> pipeline) {
    requireState(State.PREPARING);
    this.inputTransformer = inputTransformer.andThen(requireNonNull(pipeline));
    return this;
  }

  @Override
  synchronized public <S extends Supplier<Stream<String>>> S stdin() {
    if (this.stdin == null) {
      this.stdin = new StreamableQueue<>(100);
    }
    //noinspection unchecked
    return (S) this.stdin;
  }

  @Override
  synchronized public Cmd connect(Cmd cmd) {
    requireState(State.PREPARING);
    this.downstreams.add(cmd);
    return this;
  }

  /**
   * @return A stream
   */
  @Override
  synchronized public Stream<String> stream() {
    LOGGER.info("BEGIN:{}", this);
    requireState(State.PREPARING);
    this.process = startProcess(this.shell, this.command.get(), this.cwd, this.env, composeProcessConfig());
    this.state = State.RUNNING;
    Stream<String> ret = Stream.concat(process.stream(), Stream.of(CompatIoUtils.SENTINEL))
        .peek(s -> LOGGER.trace("BEFORE:{}:{}", this, s))
        .filter(o -> {
          if (o == CompatIoUtils.SENTINEL) {
            close();
            ////
            // A sentinel shouldn't be passed to following stages.
            return false;
          }
          return true;
        })
        .peek(o -> {
          if (!process.isAlive())
            if (!exitValueChecker.test(process.exitValue())) {
              throw new UnexpectedExitValueException(
                  this.process.exitValue(),
                  this.toString(),
                  this.process.getPid()
              );
            }
        })
        .map(o -> (String) o);
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
      Selector.Builder<String> builder = new Selector.Builder<String>(String.format("Cmd:%s", this))
          .add(Stream.concat(up, Stream.of((String) null))
                  .peek((String s) -> {
                    if (s == null) {
                      //noinspection unchecked
                      downstreams.stream()
                          .map(Cmd::stdin)
                          .filter(i -> i instanceof Consumer)
                          .map(Consumer.class::cast)
                          // Close stream connected to the consumer by sending null
                          .forEach((Consumer c) -> c.accept(null));
                    }
                  }),
              nop(), false);
      downstreams.forEach(each -> builder.add(
          each.stream(),
          nop(),
          true
      ));
      ret = builder.build().stream();
    }
    return ret.peek(s -> LOGGER.trace("END:{}:{}", this, s));
  }

  @Override
  synchronized public ProcessStreamer getProcessStreamer() {
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
  public Supplier<String> getCommand() {
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
    return String.format("%s '%s'",
        this.shell,
        this.command);
  }

  public void dump() {
    LOGGER.debug("{}.alive={}", this, this.process.isAlive());
    downstreams.forEach((Cmd cmd) -> ((CompatCmdImpl) cmd).dump());
  }

  synchronized private void close(boolean immediate) {
    LOGGER.debug("BEGIN:{};immediate={}", this, immediate);
    requireState(State.RUNNING, State.CLOSED);
    boolean abort = immediate || (!this.process.isAlive() && !this.exitValueChecker.test(this.process.exitValue()));
    LOGGER.trace("INFO:{};abort={}", this, abort);
    if (this.state == State.CLOSED)
      return;
    try {
      LOGGER.trace("INFO:{};isAlive={}", this, this.process.isAlive());
      if (abort)
        this._abort();
      int exitValue;
      if (this.process.isAlive())
        do {
          exitValue = this._waitFor();
        } while (this.process.isAlive());
      else
        exitValue = this.process.exitValue();

      ////
      // By this point, the process should be finished already.
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
            StreamUtils.nop()
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
      this.state = State.CLOSED;
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
      Thread.currentThread().interrupt();
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

  private static ProcessStreamer startProcess(Shell shell, String command, File cwd, Map<String, String> env, StreamableProcess.Config processConfig) {
    return CompatIoUtils.compatProcessStreamer(
        shell, command, cwd, env,
        processConfig.charset(),
        new ProcessStreamer.StreamOptions(true, "STDOUT", true, true),
        new ProcessStreamer.StreamOptions(true, "STDERR", true, true),
        100,
        3
    );
  }
}
