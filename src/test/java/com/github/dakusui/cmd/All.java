package com.github.dakusui.cmd;

import com.github.dakusui.cmd.scenario.PipelineTest;
import com.github.dakusui.cmd.scenario.ScenarioTest;
import com.github.dakusui.cmd.ut.*;
import com.github.dakusui.cmd.ut.io.LineReaderTest;
import com.github.dakusui.cmd.ut.io.RingBufferedLineWriterTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    CmdTest.class,
    PipelineTest.class,
    CmdTeeTest.class,
    CmdStateTest.class,
    SelectorTest.class,
    ScenarioTest.class,
    TeeTest.class,
    LineReaderTest.class,
    RingBufferedLineWriterTest.class,
    CommandUtilsTest.class,
    TeeTimeoutTest.class
})
public class All {
}
