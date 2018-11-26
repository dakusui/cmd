package com.github.dakusui.cmd.utils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({
    java.lang.annotation.ElementType.METHOD
})
public @interface Repeat {
  int times();
}