package com.github.dakusui.cmd.pipeline;

interface Pipe<T> extends Upstream<T>, Downstream<T> {
}
