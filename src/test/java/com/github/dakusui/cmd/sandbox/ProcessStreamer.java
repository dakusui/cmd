package com.github.dakusui.cmd.sandbox;

import com.github.dakusui.cmd.Shell;
import com.github.dakusui.cmd.core.IoUtils;
import com.github.dakusui.cmd.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.core.Checks.greaterThan;
import static com.github.dakusui.cmd.core.Checks.requireArgument;
import static com.github.dakusui.cmd.compat.CompatIoUtils.flowControlValve;
import static com.github.dakusui.cmd.core.IoUtils.toConsumer;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

public class ProcessStreamer {
  private static final Logger                     LOGGER = LoggerFactory.getLogger(ProcessStreamer.class);
  private final        Process                    process;
  private final        Stream<String>             stream;
  private final        Charset                    charset;
  private final        int                        queueSize;
  private final        IoUtils.RingBuffer<String> ringBuffer;

  public ProcessStreamer(Shell shell, String command, File cwd, Map<String, String> env, Charset charset,
      boolean stdoutLogged, boolean stdoutTailed, boolean stdoutConnected,
      boolean stderrLogged, boolean stderrTailed, boolean stderrConnected,
      int queueSize, int ringBufferSize) {
    this.process = createProcess(requireNonNull(shell), requireNonNull(command), requireNonNull(cwd), requireNonNull(env));
    this.charset = requireNonNull(charset);
    this.queueSize = requireArgument(queueSize, greaterThan(0));
    this.ringBuffer = IoUtils.RingBuffer.create(requireArgument(ringBufferSize, greaterThan(0)));
    this.stream = IoUtils.merge(
        this.queueSize,
        configureStream(
            IoUtils.stream(this.process.getInputStream(), charset),
            stdoutLogged, "STDOUT", stdoutTailed, stdoutConnected),
        configureStream(
            IoUtils.stream(this.process.getErrorStream(), charset),
            stderrLogged, "STDERR", stderrTailed, stderrConnected));
  }

  public void connect(Stream<String> stdin) {
    requireNonNull(stdin)
        .forEach(flowControlValve(toConsumer(this.process.getOutputStream(), charset), queueSize));
  }

  public Stream<String> stream() {
    return this.stream;
  }

  public int getPid() {
    return getPid(this.process);
  }

  public int waitFor() throws InterruptedException {
    LOGGER.debug("BEGIN:{}", this);
    try {
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

  private Stream<String> configureStream(Stream<String> stream, boolean logged, String loggingTag, boolean tailed, boolean connected) {
    Stream<String> ret = stream;
    if (logged)
      ret = ret.peek(s -> LOGGER.trace("{}:{}", loggingTag, s));
    if (tailed)
      ret = ret.peek(ringBuffer::write);
    if (!connected)
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
      throw new RuntimeException(String.format("PID isn't available on this platform. (%s)", e.getClass().getSimpleName()), e);
    }
    return ret;
  }

  class Builder {

  }
}
