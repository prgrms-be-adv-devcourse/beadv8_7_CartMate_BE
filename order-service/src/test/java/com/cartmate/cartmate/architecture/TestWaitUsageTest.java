package com.cartmate.cartmate.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@AnalyzeClasses(packages = "com.cartmate.cartmate",
        importOptions = {ImportOption.OnlyIncludeTests.class, OrderServiceClassesOnly.class})
class TestWaitUsageTest {

    private static final String WAIT_ON_STATE = "a fixed sleep is slow on fast machines and flaky on slow CI; wait on state instead: "
            + "KafkaTestTopics.awaitUntil / awaitConsumed / awaitRecord, LockWaits.awaitBlockedOn, "
            + "or a CountDownLatch / Future.get with a timeout";

    @ArchTest
    static final ArchRule w1_testsMustNotCallThreadSleep =
            noClasses()
                    .should().callMethod(Thread.class, "sleep", long.class)
                    .orShould().callMethod(Thread.class, "sleep", long.class, int.class)
                    .orShould().callMethod(Thread.class, "sleep", Duration.class)
                    .as("W1 order-service tests must not call Thread.sleep")
                    .because(WAIT_ON_STATE);

    @ArchTest
    static final ArchRule w2_onlySupportPollersMayCallTimeUnitSleep =
            noClasses().that().resideOutsideOfPackage("com.cartmate.cartmate.support..")
                    .should().callMethod(TimeUnit.class, "sleep", long.class)
                    .as("W2 order-service tests outside support must not call TimeUnit.sleep")
                    .because("TimeUnit.sleep is Thread.sleep in disguise; only the polling interval inside support wait helpers may sleep. "
                            + WAIT_ON_STATE);
}
