package com.github.dakusui.cmd.core;

import com.github.dakusui.cmd.Shell;
import com.github.dakusui.cmd.exceptions.CommandInterruptionException;
import com.github.dakusui.cmd.exceptions.Exceptions;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

public class StreamableProcess extends Process {
  private final Process                process;
  private final Stream<String>         stdout;
  private final Stream<String>         stderr;
  private final Consumer<String>       stdin;
  private final CompatSelector<String> selector;
  private final ExecutorService        threadPool;

  public StreamableProcess(Shell shell, String command, Config config) {
    this.process = createProcess(shell, command);
    this.stdout = IoUtils.toStream(getInputStream(), config.charset());
    this.stderr = IoUtils.toStream(getErrorStream(), config.charset());
    this.stdin = IoUtils.toConsumer(this.getOutputStream(), config.charset());
    this.threadPool = Executors.newFixedThreadPool(3);
    this.selector = createSelector(config, this.threadPool);
    System.out.println("cmd:" + command);
  }

  private static Process createProcess(Shell shell, String command) {
    try {
      return Runtime.getRuntime().exec(
          Stream.concat(
              Stream.concat(
                  Stream.of(shell.program()),
                  shell.options().stream()
              ),
              Stream.of(command)
          ).collect(toList()).toArray(new String[shell.options().size() + 2])
      );
    } catch (IOException e) {
      throw Exceptions.wrap(e);
    }
  }

  /**
   * stdin
   */
  @Override
  public OutputStream getOutputStream() {
    return new BufferedOutputStream(process.getOutputStream());
  }

  /**
   * stdout
   */
  @Override
  public InputStream getInputStream() {
    return new BufferedInputStream(process.getInputStream());
  }

  /**
   * stderr
   */
  @Override
  public InputStream getErrorStream() {
    return new BufferedInputStream(process.getErrorStream());
  }

  @Override
  public int waitFor() throws InterruptedException {
    return process.waitFor();
  }

  @Override
  public int exitValue() {
    return process.exitValue();
  }

  @Override
  public void destroy() {
    System.out.println("StreamableProcess:destroy:started");
    try {
      process.destroy();
    } finally {
      selector.close();
      System.out.println("StreamableProcess:destroy:finished");
    }
  }

  public void close() {
    try {
      System.out.println("closeStreams:closing:selector");
      this.selector.close();
      System.out.println("closeStreams:closed:selector");
      closeStreams();
    } finally {
      System.out.println("threadpool shutting down");
      shutdownAndAwaitTermination(this.threadPool);
      System.out.println("threadpool shut down");
    }
  }

  private void closeStreams() {
    try {
      System.out.println("closeStreams:sendNull");
      this.stdin().accept(null);
    } finally {
      for (Stream<String> eachStream : asList(this.stdout, this.stderr)) {
        System.out.println("closeStreams:closing:" + eachStream);
        eachStream.close();
      }
    }
  }

  private static void shutdownAndAwaitTermination(ExecutorService pool) {
    pool.shutdown(); // Disable new tasks from being submitted
    try {
      System.out.println("pool status:" + pool);
      pool.awaitTermination(1, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      throw new CommandInterruptionException();
    }
  }

  /**
   * Returns a {@code Stream<String>} object that represents standard output
   * of the underlying process.
   */
  public Stream<String> stdout() {
    return this.stdout;
  }

  /**
   * Returns a {@code Stream<String>} object that represents standard error
   * of the underlying process.
   */
  public Stream<String> stderr() {
    return this.stderr;
  }

  /**
   * Returns a {@code Consumer<String>} object that represents standard input
   * of the underlying process.
   */
  public Consumer<String> stdin() {
    return this.stdin;
  }

  public int getPid() {
    return getPid(this.process);
  }

  private CompatSelector<String> createSelector(Config config, ExecutorService excutorService) {
    return new CompatSelector.Builder<String>(100)
        .add(config.stdin(), this.stdin())
        .add(
            this.stdout()
                .map(s -> {
                  config.stdoutConsumer().accept(s);
                  return s;
                })
                .filter(config.stdoutFilter())
        )
        .add(
            this.stderr()
                .map(s -> {
                  config.stderrConsumer().accept(s);
                  return s;
                })
                .filter(config.stderrFilter())
        )
        .withExecutorService(excutorService)
        .build();
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

  public CompatSelector<String> getSelector() {
    return selector;
  }

  public interface Config {
    Stream<String> stdin();

    Consumer<String> stdoutConsumer();

    Predicate<String> stdoutFilter();

    Consumer<String> stderrConsumer();

    Predicate<String> stderrFilter();

    IntPredicate exitValueChecker();

    Charset charset();

    static Config create() {
      return builder().build();
    }

    static Config.Builder builder() {
      return builder(Stream.empty());
    }

    static Config.Builder builder(Stream<String> stdin) {
      return new Config.Builder(stdin);
    }

    class Builder {
      private static final Consumer<String> NOP = s -> {
      };

      Stream<String>    stdin;
      Consumer<String>  stdoutConsumer;
      Predicate<String> stdoutFilter;
      Consumer<String>  stderrConsumer;
      Predicate<String> stderrFilter;
      IntPredicate      exitValueChecker;
      Charset           charset;

      public Builder(Stream<String> stdin) {
        this.configureStdin(stdin);
        this.charset(Charset.defaultCharset());
        this.checkExitValueWith(value -> value == 0);
        this.configureStdout(NOP, s -> true);
        this.configureStderr(NOP, s -> false);
      }

      public Builder configureStdin(Stream<String> stdin) {
        this.stdin = Objects.requireNonNull(stdin);
        return this;
      }

      public Builder configureStdout(Consumer<String> consumer) {
        return this.configureStdout(consumer, this.stdoutFilter);
      }

      public Builder configureStdout(Consumer<String> consumer, Predicate<String> stdoutFilter) {
        this.stdoutConsumer = Objects.requireNonNull(consumer);
        this.stdoutFilter = Objects.requireNonNull(stdoutFilter);
        return this;
      }

      public Builder configureStderr(Consumer<String> consumer) {
        return this.configureStderr(consumer, this.stderrFilter);
      }

      public Builder configureStderr(Consumer<String> consumer, Predicate<String> stdoutFilter) {
        this.stderrConsumer = Objects.requireNonNull(consumer);
        this.stderrFilter = Objects.requireNonNull(stdoutFilter);
        return this;
      }

      public Builder checkExitValueWith(IntPredicate exitValueChecker) {
        this.exitValueChecker = Objects.requireNonNull(exitValueChecker);
        return this;
      }

      public Builder charset(Charset charset) {
        this.charset = Objects.requireNonNull(charset);
        return this;
      }

      public Config build() {
        return new Impl(this);
      }

    }

    class Impl implements Config {
      private Builder builder;

      Impl(Builder builder) {
        this.builder = builder;
      }

      @Override
      public Stream<String> stdin() {
        return Stream.concat(
            builder.stdin,
            Stream.of((String) null)
        );
      }

      @Override
      public Consumer<String> stdoutConsumer() {
        return builder.stdoutConsumer;
      }

      @Override
      public Consumer<String> stderrConsumer() {
        return builder.stderrConsumer;
      }

      @Override
      public Predicate<String> stdoutFilter() {
        return builder.stdoutFilter;
      }

      @Override
      public Predicate<String> stderrFilter() {
        return builder.stderrFilter;
      }

      @Override
      public IntPredicate exitValueChecker() {
        return builder.exitValueChecker;
      }

      @Override
      public Charset charset() {
        return builder.charset;
      }
    }
  }
}
