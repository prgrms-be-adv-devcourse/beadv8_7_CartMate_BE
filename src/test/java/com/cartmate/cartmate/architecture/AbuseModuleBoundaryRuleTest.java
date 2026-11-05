package com.cartmate.cartmate.architecture;

import com.cartmate.cartmate.architecture.fixture.boundedContext.etcService.abuse.app.FixtureAbuseFacade;
import com.cartmate.cartmate.architecture.fixture.boundedContext.etcService.abuse.app.FixtureAbuseUseCase;
import com.cartmate.cartmate.architecture.fixture.boundedContext.etcService.abuse.domain.FixtureAbuseEntity;
import com.cartmate.cartmate.architecture.fixture.boundedContext.etcService.abuse.in.FixtureSameTransactionListener;
import com.cartmate.cartmate.architecture.fixture.boundedContext.orderService.cart.app.FixtureCartService;
import com.cartmate.cartmate.architecture.fixture.boundedContext.orderService.cart.out.FixtureAbuseAdapter;
import com.cartmate.cartmate.architecture.fixture.boundedContext.orderService.cart.out.FixtureEntityAdapter;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AbuseModuleBoundaryRuleTest {

    private static final JavaClasses FIXTURES = new ClassFileImporter().importClasses(
            FixtureAbuseFacade.class, FixtureAbuseUseCase.class, FixtureAbuseEntity.class,
            FixtureCartService.class, FixtureAbuseAdapter.class, FixtureEntityAdapter.class,
            FixtureSameTransactionListener.class);

    @Test
    @DisplayName("A3은 abuse 안의 같은 트랜잭션 @EventListener를 잡는다")
    void a3CatchesSameTransactionListener() {
        List<String> violations = AbuseModuleBoundaryTest.a3_abuseMustNotUseSameTransactionEventListener
                .evaluate(FIXTURES).getFailureReport().getDetails();

        assertThat(violations).anyMatch(line -> line.contains("FixtureSameTransactionListener.on"));
    }

    @Test
    @DisplayName("A1은 abuse가 다른 모듈 클래스를 직접 쓰면 잡는다")
    void a1CatchesAbuseDependingOnOtherModule() {
        List<String> violations = AbuseModuleBoundaryTest.a1_abuseMustNotDependOnOtherBoundedContexts
                .evaluate(FIXTURES).getFailureReport().getDetails();

        assertThat(violations).anyMatch(line -> line.contains("FixtureAbuseUseCase") && line.contains("FixtureCartService"));
        assertThat(violations).noneMatch(line -> line.startsWith("Field <" + FixtureCartService.class.getName()));
    }

    @Test
    @DisplayName("A2는 다른 모듈의 out 어댑터가 abuse Facade를 부르는 것만 허용한다")
    void a2AllowsOnlyAdapterToFacade() {
        List<String> violations = AbuseModuleBoundaryTest.a2_otherModulesMustNotDependOnAbuse
                .evaluate(FIXTURES).getFailureReport().getDetails();

        assertThat(violations).anyMatch(line -> line.contains("FixtureCartService") && line.contains("FixtureAbuseFacade"));
        assertThat(violations).anyMatch(line -> line.contains("FixtureEntityAdapter") && line.contains("FixtureAbuseEntity"));
        assertThat(violations).noneMatch(line -> line.contains("FixtureAbuseAdapter"));
    }
}
