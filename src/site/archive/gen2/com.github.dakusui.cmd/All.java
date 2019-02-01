package com.github.dakusui.cmd;

import com.github.dakusui.cmd.compatut.CmdStateTest;
import com.github.dakusui.cmd.compatut.CmdTest;
import com.github.dakusui.cmd.compatut.CommandUtilsTest;
import com.github.dakusui.cmd.compatut.PipelinedCmdTest;
import com.github.dakusui.cmd.compatut.SelectorTest;
import com.github.dakusui.cmd.compatut.StreamableProcessTest;
import com.github.dakusui.cmd.compatut.StreamableQueueTest;
import com.github.dakusui.cmd.compatut.io.StreamUtilsTest;
import com.github.dakusui.cmd.compatut.io.LineReaderTest;
import com.github.dakusui.cmd.compatut.io.RingBufferedLineWriterTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    CmdStateTest.class,
    CmdTest.class,
    CommandUtilsTest.class,
    StreamUtilsTest.class,
    LineReaderTest.class,
    PipelinedCmdTest.class,
    SelectorTest.class,
    StreamableProcessTest.class,
    StreamableQueueTest.class,
    RingBufferedLineWriterTest.class,
})
public class All {
}
