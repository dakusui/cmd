package com.github.dakusui.cmd.ut;

import com.github.dakusui.crest.core.Matcher;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.github.dakusui.cmd.utils.ConcurrencyUtils.shutdownThreadPoolAndAwaitTermination;
import static com.github.dakusui.crest.Crest.*;
import static java.util.stream.Collectors.toList;

class SplittingConnectorTest {
}
