package com.cartmate.cartmate.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.cartmate.cartmate.OrderServiceClockConfig;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@AnalyzeClasses(packages = "com.cartmate.cartmate",
        importOptions = {ImportOption.DoNotIncludeTests.class, OrderServiceClassesOnly.class})
class OrderServiceClockUsageTest {

    @ArchTest
    static final ArchRule t1_mainMustReadTimeFromInjectedClock =
            noClasses().that().doNotHaveFullyQualifiedName(OrderServiceClockConfig.class.getName())
                    .should().callMethod(LocalDateTime.class, "now")
                    .orShould().callMethod(LocalDate.class, "now")
                    .orShould().callMethod(Instant.class, "now")
                    .as("T1 order-service main must not call LocalDateTime.now(), LocalDate.now() or Instant.now() without a Clock")
                    .because("inject java.time.Clock and call LocalDateTime.now(clock), LocalDate.now(clock) or clock.instant(), "
                            + "so tests can pin time with Clock.fixed; only OrderServiceClockConfig touches the system clock");
}
