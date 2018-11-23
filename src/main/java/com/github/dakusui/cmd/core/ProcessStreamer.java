package com.github.dakusui.cmd.core;

import com.github.dakusui.cmd.Shell;
import com.github.dakusui.cmd.core.StreamUtils.CloseableStringConsumer;
import com.github.dakusui.cmd.core.StreamUtils.RingBuffer;
import com.github.dakusui.cmd.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.core.Checks.greaterThan;
import static com.github.dakusui.cmd.core.Checks.requireArgument;
import static com.github.dakusui.cmd.core.StreamUtils.toCloseableStringConsumer;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * A class to wrap a {@code Process} object and to use it safely and easily.
 */
public class ProcessStreamer {
  private static final Logger                  LOGGER = LoggerFactory.getLogger(ProcessStreamer.class);
  private final        InputStream             stderr;
  private final        InputStream             stdout;
  private final        Process                 process;
  private final        Charset                 charset;
  private final        int                     queueSize;
  private final        Supplier<String>        formatter;
  private final        StreamOptions           stdoutOptions;
  private final        StreamOptions           stderrOptions;
  private final        RingBuffer<String>      ringBuffer;
  private final        ExecutorService         threadPool;
  private              Stream<String>          output;
  private final        Stream<String>          stdin;
  private              CloseableStringConsumer input;

  /**
   * Drains data from {@code stream} to the underlying process.
   *
   * @param stream A data stream to be drained to the process.
   */
  protected void drain(Stream<String> stream) {
    requireNonNull(stream);
    LOGGER.debug("Begin draining");
    stream.forEach(this.input::writeLine);
    LOGGER.debug("End draining");
    this.close();
  }

  /**
   * Closes {@code stdin} of this process.
   */
  private void close() {
    LOGGER.debug("Closing");
    this.input.close();
    LOGGER.debug("Closed");
  }

  /**
   * Streams data from the underlying process.
   *
   * @return data stream.
   */
  public Stream<String> stream() {
    ensureOutputInitialized();
    return this.output;
  }

  /**
   * Returns  a pid of  a process.
   *
   * @return PID of a UNIX process.
   */
  public int getPid() {
    return getPid(this.process);
  }

  /**
   * You need to call {@link ProcessStreamer#drain(Stream)} and {@link ProcessStreamer#stream()}
   * methods on this object.
   * Otherwise this method will wait forever.
   *
   * @return exit code of the underlying
   * @throws InterruptedException if the current thread is
   *                              {@linkplain Thread#interrupt() interrupted} by another
   *                              thread while it is waiting, then the wait is ended and
   *                              an {@link InterruptedException} is thrown.
   */
  public int waitFor() throws InterruptedException {
    this.ensureOutputInitialized();
    this.threadPool.shutdown();
    while (!this.threadPool.isTerminated()) {
      this.threadPool.awaitTermination(1, MILLISECONDS);
    }
    return this.process.waitFor();
  }

  public int exitValue() {
    return this.process.exitValue();
  }

  public void destroy() {
    if (this.process.isAlive()) {
      this.threadPool.shutdownNow();
      this.process.destroy();
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
    return this.process.isAlive();
  }

  @Override
  public String toString() {
    return formatter.get();
  }

  private ProcessStreamer(Shell shell, String command, File cwd, Map<String, String> env, Charset charset,
      Stream<String> stdin, StreamOptions stdoutOptions,
      StreamOptions stderrOptions,
      int queueSize, int ringBufferSize) {
    this.stdin = stdin;
    this.process = createProcess(shell, command, cwd, env);
    this.stdout = this.process.getInputStream();
    this.stderr = this.process.getErrorStream();
    this.charset = charset;
    this.queueSize = queueSize;
    final RingBuffer<String> ringBuffer = RingBuffer.create(ringBufferSize);
    this.ringBuffer = ringBuffer;
    this.stdoutOptions = stdoutOptions;
    this.stderrOptions = stderrOptions;
    this.formatter = () -> {
      synchronized (this.ringBuffer) {
        return format("%s:%s:...%s", shell, command, ringBuffer.stream().collect(joining(";")));
      }
    };
    this.threadPool = Executors.newFixedThreadPool(2 + (stdin != null ? 1 : 0));
    this.ensureInputInitialized();
    if (stdin == null)
      this.close();
    else
      threadPool.submit(() -> this.drain(stdin));
  }

  private synchronized void ensureInputInitialized() {
    if (this.input == null) {
      LOGGER.debug("Begin initialization (input)");
      this.input = toCloseableStringConsumer(this.process.getOutputStream(), this.charset);
      LOGGER.debug("End initialization (input)");
    }
  }

  /**
   * This method cannot be called from inside constructor because get{Input,Error}Stream
   * may block
   */
  private synchronized void ensureOutputInitialized() {
    if (this.output == null) {
      LOGGER.debug("Begin initialization (output)");
      this.output = StreamUtils.merge(
          this.threadPool,
          this.queueSize,
          configureStream(
              StreamUtils.stream(this.stdout, charset),
              ringBuffer,
              stdoutOptions),
          configureStream(
              StreamUtils.stream(this.stderr, charset),
              ringBuffer,
              stderrOptions));
      LOGGER.debug("End initialization (output)");
    }
  }

  private Stream<String> configureStream(Stream<String> stream, RingBuffer<String> ringBuffer, StreamOptions options) {
    Stream<String> ret = stream;
    if (options.isLogged())
      ret = ret.peek(s -> LOGGER.trace("{}:{}", options.getLoggingTag(), s));
    if (options.isTailed())
      ret = ret.peek(elem -> {
        synchronized (ringBuffer) {
          ringBuffer.write(elem);
        }
      });
    if (!options.isConnected())
      ret = ret.filter(s -> false);
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

    private final Shell               shell;
    private       String              command;
    private       File                cwd;
    private final Map<String, String> env            = new HashMap<>();
    private       StreamOptions       stdoutOptions  = new StreamOptions(true, "STDOUT", true, true);
    private       StreamOptions       stderrOptions  = new StreamOptions(true, "STDERR", true, true);
    private       Charset             charset        = Charset.defaultCharset();
    private       int                 queueSize      = 5000;
    private       int                 ringBufferSize = 100;
    private       Stream<String>      stdin;

    public Builder(Shell shell, String command) {
      this.shell = requireNonNull(shell);
      this.command = requireNonNull(command);
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
          stdin, this.stdoutOptions,
          this.stderrOptions,
          this.queueSize,
          this.ringBufferSize
      );
    }

  }

  public static class StreamOptions {
    private final boolean logged;
    private final String  loggingTag;
    private final boolean tailed;
    private final boolean connected;

    public StreamOptions(boolean logged, String loggingTag, boolean tailed, boolean connected) {
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

  public static ProcessStreamer compatProcessStreamer(Shell shell, String command, File cwd, Map<String, String> env, Charset charset,
      StreamOptions stdoutOptions,
      StreamOptions stderrOptions,
      int queueSize, int ringBufferSize) {
    return new ProcessStreamer(shell, command, cwd, env, charset, null, stdoutOptions, stderrOptions, queueSize, ringBufferSize) {
      @Override
      public void drain(Stream<String> stream) {
        super.drain(stream.peek(s -> {
          if (s == null)
            super.close();
        }));
      }
    };
  }
}
