package com.github.dakusui.cmd.ut;

import com.github.dakusui.cmd.Cmd;
import com.github.dakusui.cmd.utils.TestUtils;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

public class PipelineTest extends TestUtils.TestBase {
  private List<String> out = Collections.synchronizedList(new LinkedList<>());

  @Test(timeout = 3_000)
  public void pipe() {
    Cmd.cmd(
        "echo hello && echo world"
    ).connectTo(
        Cmd.cmd("cat -n")
    ).connectTo(
        Cmd.cmd("sort -r")
    ).connectTo(
        Cmd.cmd("sed 's/hello/HELLO/'")
    ).connectTo(
        Cmd.cmd("sed -E 's/^ +//'")
    ).stream(
    ).map(
        s -> String.format("<%s>", s)
    ).forEach(
        System.out::println
    );
  }

  @Test(timeout = 15_000)
  public void tee10K() {
    Cmd.cmd(
        "seq 1 10000"
    ).readFrom(
        () -> Stream.of((String) null)
    ).connectTo(
        Cmd.cat().pipeline(
            stream -> stream.map(
                s -> "LEFT:" + s
            )
        ),
        Cmd.cat().pipeline(
            stream -> stream.map(
                s -> "RIGHT:" + s
            )
        )
    ).stream(
    ).forEach(
        System.out::println
    );

  }

  @Test(timeout = 15_000)
  public void tee100K() {
    Cmd.cmd(
        "seq 1 100000"
    ).connectTo(
        Cmd.cat().pipeline(
            stream -> stream.map(
                s -> "LEFT:" + s
            )
        ),
        Cmd.cat().pipeline(
            stream -> stream.map(
                s -> "RIGHT:" + s
            )
        )
    ).stream(
    ).forEach(
        System.out::println
    );
  }

  @Test(timeout = 15_000)
  public void pipe10K() throws InterruptedException {
    Cmd.cmd(
        "seq 1 10000"
    ).connectTo(
        Cmd.cat().pipeline(
            st -> st.map(
                s -> "DOWN:" + s
            )
        )
    ).stream(
    ).forEach(
        System.out::println
    );
  }

  @Test(timeout = 15_000)
  public void pipe100K() throws InterruptedException {
    Cmd.cmd(
        "seq 1 100000"
    ).connectTo(
        Cmd.cat().pipeline(
            st -> st.map(
                s -> "DOWN:" + s
            )
        )
    ).stream(
    ).forEach(
        System.out::println
    );
  }


}
