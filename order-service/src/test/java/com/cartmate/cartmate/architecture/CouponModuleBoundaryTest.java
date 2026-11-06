package com.cartmate.cartmate.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.context.event.EventListener;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

// 쿠폰 모듈 경계. AbuseModuleBoundaryTest와 같은 방식으로 검사한다
@AnalyzeClasses(packages = "com.cartmate.cartmate", importOptions = ImportOption.DoNotIncludeTests.class)
class CouponModuleBoundaryTest {

    private static final String BOUNDED_CONTEXT = "..boundedContext..";
    private static final String COUPON = "..boundedContext.orderService.coupon..";
    private static final String ANY_MODULE_OUT = "..boundedContext.*.*.out..";

    // C1 쿠폰은 boundedContext 아래 쿠폰 밖 패키지(cart, order, abuse 등)에 의존하지 않는다.
    // 예외는 coupon.out 어댑터가 다른 모듈 app의 Facade를 부르는 것뿐. 라이브러리·common은 막지 않는다 (AbuseModuleBoundaryTest A1과 같은 방식)
    @ArchTest
    static final ArchRule c1_couponMustNotDependOnOtherModules = classes().that().resideInAPackage(COUPON)
            .should(dependOnlyThroughAdapterFacades(
                    resideInAPackage("..boundedContext.orderService.coupon.out.."),
                    resideInAPackage(BOUNDED_CONTEXT).and(not(resideInAPackage(COUPON))),
                    resideInAPackage("..boundedContext.*.*.app..").and(simpleNameEndingWith("Facade"))))
            .as("C1 쿠폰은 다른 업무 모듈에 의존하지 않는다. coupon.out 어댑터가 다른 모듈 app의 Facade를 부르는 것만 허용")
            .because("쿠폰은 다른 업무 모듈(cart, order, abuse 등)의 클래스를 직접 쓰지 않고 shared 계약과 out 어댑터로만 만난다");

    // C2 다른 모듈은 자기 out 어댑터에서 CouponFacade와 쿠폰 dto만 부른다
    @ArchTest
    static final ArchRule c2_otherModulesMustUseCouponOnlyThroughFacade = classes().that(not(resideInAPackage(COUPON)))
            .should(dependOnlyThroughAdapterFacades(
                    resideInAPackage(ANY_MODULE_OUT),
                    resideInAPackage(COUPON),
                    resideInAPackage("..boundedContext.orderService.coupon.app..").and(simpleName("CouponFacade"))
                            .or(resideInAPackage("..boundedContext.orderService.coupon.dto.."))))
            .as("C2 쿠폰 밖의 클래스는 쿠폰을 쓰지 않는다. 다른 모듈 out 어댑터가 CouponFacade와 쿠폰 dto를 부르는 것만 허용")
            .because("쿠폰의 domain, 저장소, UseCase를 직접 쓰면 쿠폰을 다른 서버로 옮길 때 어댑터 한 곳만 바꿀 수 없다");

    // C3 같은 트랜잭션 @EventListener 금지 (커밋 후 Outbox 릴레이로 받는다)
    @ArchTest
    static final ArchRule c3_couponMustNotUseSameTransactionEventListener = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage(COUPON)
            .should().beAnnotatedWith(EventListener.class)
            .as("C3 쿠폰 안의 메서드에 @EventListener를 붙이지 않는다 (Outbox 릴레이 메시지로 받는다)")
            .because("같은 트랜잭션 리스너는 다른 모듈 트랜잭션 안에서 쿠폰 테이블을 바꿔, 쿠폰 쪽 실패가 가입까지 되돌린다");

    private static ArchCondition<JavaClass> dependOnlyThroughAdapterFacades(DescribedPredicate<JavaClass> adapter,
                                                                         DescribedPredicate<JavaClass> guarded,
                                                                         DescribedPredicate<JavaClass> allowedFacade) {
        return new ArchCondition<>("not depend on " + guarded.getDescription()
                + ", except " + adapter.getDescription() + " calling " + allowedFacade.getDescription()) {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                boolean fromAdapter = adapter.test(origin);
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass().getBaseComponentType();
                    if (!guarded.test(target) || (fromAdapter && allowedFacade.test(target))) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        };
    }
}
