package com.github.dakusui.cmd.core;

import com.github.dakusui.cmd.Shell;
import com.github.dakusui.cmd.core.IoUtils.RingBuffer;
import com.github.dakusui.cmd.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.core.Checks.greaterThan;
import static com.github.dakusui.cmd.core.Checks.requireArgument;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class ProcessStreamer {
  private static final Logger             LOGGER = LoggerFactory.getLogger(ProcessStreamer.class);
  private final        Process            process;
  private final        Charset            charset;
  private final        int                queueSize;
  private final        Supplier<String>   formatter;
  private final        StreamOptions      stdoutOptions;
  private final        StreamOptions      stderrOptions;
  private final        RingBuffer<String> ringBuffer;
  private              Stream<String>     output;
  private              Consumer<String>   input;

  private ProcessStreamer(Shell shell, String command, File cwd, Map<String, String> env, Charset charset,
      StreamOptions stdoutOptions,
      StreamOptions stderrOptions,
      int queueSize, int ringBufferSize) {
    this.process = createProcess(shell, command, cwd, env);
    this.charset = charset;
    this.queueSize = queueSize;
    final RingBuffer<String> ringBuffer = RingBuffer.create(ringBufferSize);
    this.ringBuffer = ringBuffer;
    this.stdoutOptions = stdoutOptions;
    this.stderrOptions = stderrOptions;
    this.formatter = () -> {
      synchronized (this.ringBuffer) {
        return format("%s:%s:...%s", shell, command, ringBuffer.stream().collect(joining(format("%n"))));
      }
    };
  }

  public synchronized void stdin(Stream<String> stream) {
    if (this.input == null) {
      this.input = IoUtils.toStringConsumer(this.process.getOutputStream(), this.charset);
    }
    new Thread(() -> stream.peek(System.err::println).forEach(this.input)).start();
  }

  public synchronized Stream<String> stream() {
    initOutput();
    return this.output;
  }

  private void initOutput() {
    if (this.output == null)
      this.output = IoUtils.merge(
          this.queueSize,
          configureStream(
              IoUtils.stream(this.process.getInputStream(), charset),
              ringBuffer,
              stdoutOptions),
          configureStream(
              IoUtils.stream(this.process.getErrorStream(), charset),
              ringBuffer,
              stderrOptions));
  }

  public int getPid() {
    return getPid(this.process);
  }

  public synchronized int waitFor() throws InterruptedException {
    LOGGER.debug("BEGIN:{}", this);
    try {
      this.initOutput();
      return process.waitFor();
    } finally {
      LOGGER.debug("END:{}", this);
    }
  }

  public int exitValue() {
    return process.exitValue();
  }

  public void destroy() {
    process.destroy();
  }

  @Override
  public String toString() {
    return formatter.get();
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
    private       int                 queueSize      = 100;
    private       int                 ringBufferSize = 100;

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
          this.stdoutOptions,
          this.stderrOptions,
          this.queueSize,
          this.ringBufferSize
      );
    }
  }

  static class StreamOptions {
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
}
