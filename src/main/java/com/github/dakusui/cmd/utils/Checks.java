package com.github.dakusui.cmd.utils;

import java.util.function.Predicate;

public enum Checks {
  ;

  public static <V> V requireArgument(V value, Predicate<V> cond) {
    if (!cond.test(value))
      throw new IllegalArgumentException(String.format("A value '%s' did not meet the requirement:%s", value, cond));
    return value;
  }

  public static <V extends Comparable<V>> Predicate<V> greaterThan(V value) {
    return new Predicate<V>() {

      @Override
      public boolean test(V v) {
        return v.compareTo(value) > 0;
      }

      @Override
      public String toString() {
        return ">" + value;
      }
    };
  }

  public static <V extends Comparable<V>> Predicate<V> greaterThanOrEqualTo(V value) {
    return new Predicate<V>() {

      @Override
      public boolean test(V v) {
        return v.compareTo(value) >= 0;
      }

      @Override
      public String toString() {
        return ">=" + value;
      }
    };
  }
}
