package com.github.dakusui.cmd.ut;

import com.github.dakusui.cmd.Cmd;
import com.github.dakusui.cmd.utils.TestUtils;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.stream.Stream;

import static com.github.dakusui.cmd.Cmd.cmd;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PipelinedCmdTest extends TestUtils.TestBase {
  @Test
  public void simplePipe() {
    cmd(
        "echo hello && echo world"
    ).connectTo(
        cmd(
            "cat -n"
        ).connectTo(
            cmd("cat -n")
        )
    ).stream().map(
        s -> String.format("<%s>", s)
    ).forEach(
        System.out::println
    );
  }

  /**
   * Shows flakiness 7/18/2017
   * <code>
   * org.junit.runners.model.TestTimedOutException: test timed out after 5000 milliseconds
   * <p>
   * at java.io.FileInputStream.readBytes(Native Method)
   * at java.io.FileInputStream.read(FileInputStream.java:255)
   * at java.io.BufferedInputStream.read1(BufferedInputStream.java:284)
   * at java.io.BufferedInputStream.read(BufferedInputStream.java:345)
   * at java.io.BufferedInputStream.read1(BufferedInputStream.java:284)
   * at java.io.BufferedInputStream.read(BufferedInputStream.java:345)
   * at sun.nio.cs.StreamDecoder.readBytes(StreamDecoder.java:284)
   * at sun.nio.cs.StreamDecoder.implRead(StreamDecoder.java:326)
   * at sun.nio.cs.StreamDecoder.read(StreamDecoder.java:178)
   * at java.io.InputStreamReader.read(InputStreamReader.java:184)
   * at java.io.BufferedReader.fill(BufferedReader.java:161)
   * at java.io.BufferedReader.readLine(BufferedReader.java:324)
   * at java.io.BufferedReader.readLine(BufferedReader.java:389)
   * at com.github.dakusui.cmd.core.IoUtils$3.readLine(IoUtils.java:133)
   * at com.github.dakusui.cmd.core.IoUtils$3.readIfNotReadYet(IoUtils.java:124)
   * at com.github.dakusui.cmd.core.IoUtils$3.hasNext(IoUtils.java:106)
   * at java.util.Iterator.forEachRemaining(Iterator.java:115)
   * at java.util.Spliterators$IteratorSpliterator.forEachRemaining(Spliterators.java:1801)
   * at java.util.stream.AbstractPipeline.copyInto(AbstractPipeline.java:481)
   * at java.util.stream.AbstractPipeline.wrapAndCopyInto(AbstractPipeline.java:471)
   * </code>
   */
  @Test(timeout = 5_000)
  public void complexPipe() {
    cmd("echo hello && echo world").connectTo(
        cmd("cat -n").connectTo(
            cmd("sort -r").connectTo(
                cmd("sed 's/hello/HELLO/'").connectTo(
                    cmd("sed -E 's/^ +//'")
                )))
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

  @Test(timeout = 30_000)
  public void tee20K() {
    cmd(
        "seq 1 20000"
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
    cmd(
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
  public void pipe20K() throws InterruptedException {
    cmd(
        "seq 1 20000"
    ).connectTo(
        Cmd.cat().pipeline(
            st -> st.map(
                s -> "DOWN:" + s
            )
        )
    ).stream(
    ).forEach(
        System.err::println
    );
  }

  @Test(timeout = 30_000)
  public void pipe100K() throws InterruptedException {
    cmd(
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
