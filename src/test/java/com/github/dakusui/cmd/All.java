package com.github.dakusui.cmd;

import com.github.dakusui.cmd.ut.*;
import com.github.dakusui.cmd.ut.io.IoUtilsTest;
import com.github.dakusui.cmd.ut.io.LineReaderTest;
import com.github.dakusui.cmd.ut.io.RingBufferedLineWriterTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    CmdStateTest.class,
    CmdTest.class,
    CommandUtilsTest.class,
    IoUtilsTest.class,
    SelectorTest.class,
    StreamableProcessTest.class,
    StreamableQueueTest.class,
    PipelinedCmdTest.class,
    RingBufferedLineWriterTest.class,
    LineReaderTest.class,
})
public class All {
}
