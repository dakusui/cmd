package com.github.dakusui.cmd.ut;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses(value = {
    StreamUtilsTest.class,
    ProcessStreamerTest.class,
    PipelineTest.class,
    ProcessStreamerConnectionTest.class
})
public class DesignRenewal {
}
