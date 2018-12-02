package com.github.dakusui.cmd.core.process;

import com.github.dakusui.cmd.exceptions.CommandExecutionException;
import com.github.dakusui.cmd.exceptions.Exceptions;
import com.github.dakusui.cmd.utils.StreamUtils;
import com.github.dakusui.cmd.utils.StreamUtils.CloseableStringConsumer;
import com.github.dakusui.cmd.utils.StreamUtils.RingBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.utils.Checks.greaterThan;
import static com.github.dakusui.cmd.utils.Checks.requireArgument;
import static com.github.dakusui.cmd.utils.StreamUtils.nop;
import static com.github.dakusui.cmd.utils.StreamUtils.toCloseableStringConsumer;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * A class to wrap a {@code Process} object and to use it safely and easily.
 */
public class ProcessStreamer {
  class Ports {
    final InputStream  stderr;
    final InputStream  stdout;
    final OutputStream stdin;

    Ports(InputStream stderr, InputStream stdout, OutputStream stdin) {
      this.stderr = stderr;
      this.stdout = stdout;
      this.stdin = stdin;
    }
  }

  private static final Logger                  LOGGER = LoggerFactory.getLogger(ProcessStreamer.class);
  private final        Ports                   ports;
  private final        String                  commandLine;
  private final        Process                 process;
  private final        Charset                 charset;
  private final        int                     queueSize;
  private final        Supplier<String>        formatter;
  private final        StreamOptions           stdoutOptions;
  private final        StreamOptions           stderrOptions;
  private final        RingBuffer<String>      ringBuffer;
  private final        ExecutorService         threadPool;
  private final        Checker                 checker;
  private final        Shell                   shell;
  private              Stream<String>          output;
  private final        Stream<String>          input;
  private final        CloseableStringConsumer inputDestination;

  private ProcessStreamer(
      Shell shell,
      String command,
      File cwd,
      Map<String, String> env,
      Charset charset,
      Stream<String> stdin,
      StreamOptions stdoutOptions,
      StreamOptions stderrOptions,
      int queueSize,
      int ringBufferSize,
      Checker checker) {
    this.shell = shell;
    this.commandLine = command;
    this.process = createProcess(shell, this.commandLine, cwd, env);
    this.ports = new Ports(this.process.getErrorStream(), this.process.getInputStream(), this.process.getOutputStream());
    this.charset = charset;
    this.queueSize = queueSize;
    final RingBuffer<String> ringBuffer = RingBuffer.create(ringBufferSize);
    this.ringBuffer = ringBuffer;
    this.stdoutOptions = stdoutOptions;
    this.stderrOptions = stderrOptions;
    this.formatter = () -> {
      synchronized (this.ringBuffer) {
        return format("%s:%s:...%s", this.shell, this.commandLine, ringBuffer.stream().collect(joining(";")));
      }
    };
    this.checker = checker;
    this.input = stdin;
    this.threadPool = newFixedThreadPool(2 + (this.ports.stdin != null ? 1 : 0));
    this.inputDestination = initializeInput(stdin, this.ports, this.threadPool, this.charset);
    this.initializeOutput();
  }

  /**
   * Streams data from the underlying process.
   * The returned stream must be closed by a user explicitly.
   *
   * @return data stream.
   */
  public Stream<String> stream() {
    return this.output.onClose(() -> {
      this.close();
      try {
        LOGGER.debug("Closing");
        this.waitFor();
        LOGGER.debug("Closed");
      } catch (InterruptedException ignored) {
      }
    });
  }

  /**
   * Returns  a pid of  a process.
   *
   * @return PID of a UNIX process.
   */
  public int getPid() {
    synchronized (this.process) {
      return getPid(this.process);
    }
  }

  /**
   * You need to call {@link ProcessStreamer#drain(Stream, CloseableStringConsumer)} and {@link ProcessStreamer#stream()}
   * methods on this object.
   * Otherwise this method will wait forever.
   *
   * @return exit code of the underlying process.
   * @throws InterruptedException if the current thread is
   *                              {@linkplain Thread#interrupt() interrupted} by another
   *                              thread while the underlying process is waiting, then the wait is ended and
   *                              an {@link InterruptedException} is thrown.
   */
  public int waitFor() throws InterruptedException {
    synchronized (this.process) {
      shutdownThreadPoolAndAwaitTermination(threadPool);
      return checkProcessBehaviourWithChecker(this, this.checker);
    }
  }

  public int exitValue() {
    synchronized (this.process) {
      return this.process.exitValue();
    }
  }

  public void destroy() {
    synchronized (this.process) {
      if (this.process.isAlive()) {
        this.threadPool.shutdownNow();
        this.process.destroy();
      }
    }
  }

  /**
   * Returns {@code true} if the subprocess represented by this object is still
   * alive, {@code false} otherwise.
   *
   * @return {@code true} - this process is alive / {@code false} otherwise.
   * @see Process#isAlive()
   */
  public boolean isAlive() {
    synchronized (this.process) {
      return this.process.isAlive();
    }
  }

  @Override
  public String toString() {
    return formatter.get();
  }

  public static ProcessStreamer.Builder source() {
    return source(Shell.local());
  }

  public static ProcessStreamer.Builder source(Shell shell) {
    return new ProcessStreamer.Builder(shell)
        .stdin(null)
        .configureStdout(true, true, true)
        .configureStderr(true, true, false);
  }

  public static ProcessStreamer.Builder sink(Stream<String> stdin) {
    return sink(stdin, Shell.local());
  }

  public static ProcessStreamer.Builder sink(Stream<String> stdin, Shell shell) {
    return new ProcessStreamer.Builder(shell)
        .stdin(requireNonNull(stdin))
        .configureStdout(true, true, true)
        .configureStderr(true, true, false);
  }

  public static ProcessStreamer.Builder pipe(Stream<String> stdin) {
    return pipe(stdin, Shell.local());
  }

  public static ProcessStreamer.Builder pipe(Stream<String> stdin, Shell shell) {
    return new ProcessStreamer.Builder(shell)
        .stdin(requireNonNull(stdin))
        .configureStdout(true, true, true)
        .configureStderr(true, true, false);
  }

  /**
   * Drains data from {@code stream} to the underlying process.
   *
   * @param input A data stream to be drained to the process.
   */
  private static void drain(Stream<String> input, CloseableStringConsumer inputDestination) {
    requireNonNull(input);
    LOGGER.debug("Begin draining");
    input.forEach(inputDestination);
    LOGGER.debug("End draining");
    try {
      input.close();
    } finally {
      inputDestination.close();
    }
    LOGGER.debug("Closed");
  }

  /**
   * Closes {@code stdin} of this process.
   */
  protected void close() {
    try {
      if (this.input != null)
        this.input.close();
    } finally {
      this.inputDestination.close();
    }
  }

  private synchronized static CloseableStringConsumer initializeInput(Stream<String> input, Ports ports, ExecutorService threadPool, Charset charset) {
    LOGGER.debug("Begin initialization (input)");
    CloseableStringConsumer ret =
        toCloseableStringConsumer(
            new BufferedOutputStream(ports.stdin),
            charset);
    LOGGER.debug("End initialization (input)");
    ////
    // If input is not given, the stdin (, which is returned by Process#getOutputStream()
    // will be closed immediately.
    if (input == null)
      ret.close();
    else
      threadPool.submit(() -> drain(input, ret));
    return ret;
  }

  /**
   * This method cannot be called from inside constructor because get{Input,Error}Stream
   * may block
   */
  @SuppressWarnings("unchecked")
  private synchronized void initializeOutput() {
    if (this.output == null) {
      class StreamFactory implements Function<ExecutorService, Stream<String>> {
        private final InputStream           in;
        private final StreamOptions         options;
        private final Checker.StreamChecker checker;

        private StreamFactory(InputStream in, StreamOptions options, Checker.StreamChecker checker) {
          this.in = in;
          this.options = options;
          this.checker = checker;
        }

        @Override
        public Stream<String> apply(ExecutorService executorService) {
          return configureStream(
              StreamUtils.stream(this.in, charset).peek(this.checker),
              ringBuffer,
              this.options,
              threadPool
          );
        }
      }
      LOGGER.debug("Begin initialization (output)");
      this.output = StreamUtils.merge(
          this.threadPool,
          (threadPool) -> {
          },
          this.queueSize,
          Stream.of(
              new StreamFactory(this.ports.stdout, stdoutOptions, checker.forStdOut()),
              new StreamFactory(this.ports.stderr, stderrOptions, checker.forStdErr()))
              .map((StreamFactory each) -> each.apply(this.threadPool))
              .filter(Objects::nonNull)
              .toArray(Stream[]::new)
      );
      LOGGER.debug("End initialization (output)");
    }
  }

  private static Stream<String> configureStream(Stream<String> stream, RingBuffer<String> ringBuffer, StreamOptions options, ExecutorService threadPool) {
    Stream<String> ret = stream;
    if (options.isLogged())
      ret = ret.peek(s -> LOGGER.trace("{}:{}", options.getLoggingTag(), s));
    if (options.isTailed())
      ret = ret.peek(elem -> {
        synchronized (ringBuffer) {
          ringBuffer.write(elem);
        }
      });
    if (!options.isConnected()) {
      Stream<String> terminated = ret;
      threadPool.submit(() -> terminated.forEach(nop()));
      ret = null;
    }
    return ret;
  }

  private static Process createProcess(Shell shell, String command, File cwd, Map<String, String> env) {
    try {
      ProcessBuilder b = new ProcessBuilder()
          .command(
              Stream.concat(
                  Stream.concat(
                      Stream.of(shell.program()), shell.options().stream()),
                  Stream.of(command))
                  .collect(toList()))
          .directory(cwd);
      b.environment().putAll(env);
      return b.start();
    } catch (IOException e) {
      throw Exceptions.wrap(e);
    }
  }

  private int checkProcessBehaviourWithChecker(ProcessStreamer proc, ProcessStreamer.Checker checker) throws InterruptedException {
    return checker.check(proc);
  }

  private static void shutdownThreadPoolAndAwaitTermination(ExecutorService threadPool) {
    threadPool.shutdown();
    while (!threadPool.isTerminated()) {
      try {
        threadPool.awaitTermination(1, MILLISECONDS);
      } catch (InterruptedException ignored) {
      }
    }
  }

  private static int getPid(Process proc) {
    int ret;
    try {
      Field f = proc.getClass().getDeclaredField("pid");
      boolean accessible = f.isAccessible();
      f.setAccessible(true);
      try {
        ret = Integer.parseInt(f.get(proc).toString());
      } finally {
        f.setAccessible(accessible);
      }
    } catch (IllegalAccessException | NumberFormatException | SecurityException | NoSuchFieldException e) {
      throw new RuntimeException(format("PID isn't available on this platform. (%s)", e.getClass().getSimpleName()), e);
    }
    return ret;
  }

  public static class Builder {
    private       Shell               shell;
    private       String              command;
    private       File                cwd;
    private final Map<String, String> env            = new HashMap<>();
    private       StreamOptions       stdoutOptions  = new StreamOptions(true, "STDOUT", true, true);
    private       StreamOptions       stderrOptions  = new StreamOptions(true, "STDERR", true, true);
    private       Charset             charset        = Charset.defaultCharset();
    private       int                 queueSize      = 5_000;
    private       int                 ringBufferSize = 100;
    private       Stream<String>      stdin;
    private       Checker             checker;

    Builder() {
      this.checker(Checker.createDefault());
      this.shell(Shell.local());
    }

    Builder(Shell shell) {
      this();
      this.shell(shell);
    }

    public Builder(Shell shell, String command) {
      this(shell);
      this.command(command);
    }

    public Builder shell(Shell shell) {
      this.shell = requireNonNull(shell);
      return this;
    }

    public Builder command(String command) {
      this.command = requireNonNull(command);
      return this;
    }

    public Builder checker(Checker checker) {
      this.checker = requireNonNull(checker);
      return this;
    }

    public Builder configureStdout(boolean logged, boolean tailed, boolean connected) {
      this.stdoutOptions = new StreamOptions(logged, "STDOUT", tailed, connected);
      return this;
    }

    public Builder configureStderr(boolean logged, boolean tailed, boolean connected) {
      this.stderrOptions = new StreamOptions(logged, "STDERR", tailed, connected);
      return this;
    }

    public Builder stdin(Stream<String> stdin) {
      this.stdin = stdin;
      return this;
    }

    public Stream<String> stdin() {
      return this.stdin;
    }

    /**
     * Sets this process builder's working directory.
     * <p>
     * {@code cwd} can be {@code null} and it means the working directory of the
     * current Java process.
     *
     * @param cwd The new working directory
     * @return This object
     * @see ProcessBuilder#directory(File)
     */
    public Builder cwd(File cwd) {
      this.cwd = cwd;
      return this;
    }

    public Builder env(String varname, String value) {
      this.env.put(requireNonNull(varname), requireNonNull(value));
      return this;
    }

    public Builder charset(Charset charset) {
      this.charset = requireNonNull(charset);
      return this;
    }

    public Builder queueSize(int queueSize) {
      this.queueSize = requireArgument(queueSize, greaterThan(0));
      return this;
    }

    public Builder ringBufferSize(int ringBufferSize) {
      this.ringBufferSize = requireArgument(ringBufferSize, greaterThan(0));
      return this;
    }

    public ProcessStreamer build() {
      return new ProcessStreamer(
          this.shell,
          this.command,
          this.cwd,
          this.env,
          this.charset,
          this.stdin,
          this.stdoutOptions,
          this.stderrOptions,
          this.queueSize,
          this.ringBufferSize,
          this.checker
      );
    }
  }

  public static class StreamOptions {
    private final boolean logged;
    private final String  loggingTag;
    private final boolean tailed;
    private final boolean connected;

    StreamOptions(boolean logged, String loggingTag, boolean tailed, boolean connected) {
      this.logged = logged;
      this.loggingTag = loggingTag;
      this.tailed = tailed;
      this.connected = connected;
    }

    boolean isLogged() {
      return logged;
    }

    String getLoggingTag() {
      return loggingTag;
    }

    boolean isTailed() {
      return tailed;
    }

    boolean isConnected() {
      return connected;
    }
  }

  public interface Checker {
    default int check(ProcessStreamer processStreamer) throws InterruptedException, CommandExecutionException {
      final int exitCode = processStreamer.process.waitFor();
      Optional<String> mismatch = describeMismatch(exitCode);
      if (!mismatch.isPresent())
        return exitCode;
      throw new Failure(
          format("shell=[%s]:command line=[%s]%n%s%n  Recent output:%s",
              processStreamer.shell,
              processStreamer.commandLine,
              mismatch.get(),
              processStreamer.ringBuffer
                  .stream()
                  .collect(joining(format("%n    "), format("...%n    "), ""))));
    }

    default Optional<String> describeMismatch(int exitCode) {
      List<String> mismatches = new ArrayList<>(4);
      if (!exitCodeChecker().test(exitCode))
        mismatches.add(format("Expectation for exit code [%s] was not met.: exit code(actual)=%s", exitCodeChecker(), exitCode));
      if (!forStdOut().getAsBoolean())
        mismatches.add(format("Expectation for stdout [%s] was not met.", forStdOut()));
      if (!forStdErr().getAsBoolean())
        mismatches.add(format("Expectation for stderr [%s] was not met.", forStdOut()));
      return mismatches.isEmpty() ?
          Optional.empty() :
          Optional.of(mismatches.stream().collect(joining(format("%n  "), "  ", "")));
    }

    StreamChecker forStdOut();

    StreamChecker forStdErr();

    Predicate<Integer> exitCodeChecker();

    static Checker createDefault() {
      return createCheckerForExitCode(0);
    }

    static Checker createCheckerForExitCode(int acceptableExitCode) {
      return createCheckerForExitCode(new Predicate<Integer>() {
        @Override
        public boolean test(Integer value) {
          return Objects.equals(value, acceptableExitCode);
        }

        @Override
        public String toString() {
          return "==" + acceptableExitCode;
        }
      });
    }

    static Checker createCheckerForExitCode(Predicate<Integer> cond) {
      StreamChecker alwaysOk = new StreamChecker() {
        @Override
        public boolean getAsBoolean() {
          return true;
        }

        @Override
        public void accept(String s) {
        }
      };
      return new Impl(alwaysOk, alwaysOk, cond);
    }

    /**
     * An interface to check if a process's input/output streams are meeting requirements.
     * Instances of this interface returned by {@link Checker} are inserted into reference pipelines
     * that represent {@code stdout} and {@code stderr} of the process streamer
     * to which the checker belongs.
     * <p>
     * And therefore {@code StreamChecker} is able to check the process's activity is meeting
     * its requirement.
     * When it detects an unexpected data in the stream it is responsible for, the {@code get()}
     * method should return {@code false}.
     *
     * @see Checker#forStdOut()
     * @see Checker#forStdErr()
     */
    interface StreamChecker extends Consumer<String>, BooleanSupplier {
    }

    class Impl implements Checker {
      final StreamChecker      stdoutChecker;
      final StreamChecker      stderrChecker;
      final Predicate<Integer> exitCodeChecker;

      Impl(StreamChecker stdoutChecker, StreamChecker stderrChecker, Predicate<Integer> exitCodeChecker) {
        this.stdoutChecker = requireNonNull(stdoutChecker);
        this.stderrChecker = requireNonNull(stderrChecker);
        this.exitCodeChecker = requireNonNull(exitCodeChecker);
      }


      @Override
      public StreamChecker forStdOut() {
        return this.stdoutChecker;
      }

      @Override
      public StreamChecker forStdErr() {
        return this.stderrChecker;
      }

      @Override
      public Predicate<Integer> exitCodeChecker() {
        return this.exitCodeChecker;
      }
    }
  }

  public static class Failure extends CommandExecutionException {
    Failure(String msg) {
      super(msg, null);
    }
  }
}
