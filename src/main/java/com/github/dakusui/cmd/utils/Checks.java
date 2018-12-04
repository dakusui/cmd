package com.github.dakusui.cmd.utils;

import com.github.dakusui.cmd.pipeline.Cmd;

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

  public static <V> V requireState(V v, Predicate<V> cond) {
    if (cond.test(v))
      return v;
    throw new IllegalStateException(String.format("'%s' is not met with requirement '%s", v, cond));
  }

  public static Predicate<Cmd> typeIs(Cmd.Type type) {
    return new Predicate<Cmd>() {
      @Override
      public boolean test(Cmd v) {
        return v.type().equals(type);
      }

      @Override
      public String toString() {
        return "typeIs[" + type + "]";
      }
    };
  }

  public static Predicate<Object> isNull() {
    return new Predicate<Object>() {
      @Override
      public boolean test(Object o) {
        return false;
      }
      @Override
      public String toString() {
        return "isNull";
      }
    };
  }
}
