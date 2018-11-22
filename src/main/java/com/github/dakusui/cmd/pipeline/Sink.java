package com.github.dakusui.cmd.pipeline;

import java.util.function.Consumer;

interface Sink<T> extends Downstream<T> {
  void forEach(Consumer<T> consumer);
}
