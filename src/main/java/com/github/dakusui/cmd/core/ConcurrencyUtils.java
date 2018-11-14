package com.github.dakusui.cmd.core;

import java.util.function.Consumer;
import java.util.function.Predicate;

public enum ConcurrencyUtils {
  ;

  public static <T> void updateAndNotifyAll(T monitor, Consumer<T> update) {
    update.accept(monitor);
    monitor.notifyAll();
  }

  public static <T> void waitWhile(T monitor, Predicate<T> cond) {
    while (cond.test(monitor)) {
      try {
        monitor.wait();
      } catch (InterruptedException ignored) {
      }
    }
  }
}
