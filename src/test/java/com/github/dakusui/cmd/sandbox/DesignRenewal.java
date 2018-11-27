package com.github.dakusui.cmd.sandbox;

import com.github.dakusui.cmd.ut.io.StreamUtilsTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses(value = {
    StreamUtilsTest.class,
    ProcessStreamerTest.class,
    PipelineTest.class
})
public class DesignRenewal {
}
