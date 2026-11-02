package com.cartmate.cartmate.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.context.event.EventListener;

/**
 * cart · order 모듈 경계 규칙 (ARCH-1 경계 규칙 1~3 · 6).
 *
 * <p>cart와 order는 한 앱 · 한 DB에 있지만 서로 다른 서비스처럼 다룬다.
 * 모듈 간 계약은 {@code com.cartmate.cartmate.shared} 패키지로만 드러내고,
 * 다른 모듈의 내부 패키지(app · domain · out · in · dto)를 직접 참조하지 않는다.
 *
 * <p>현재 코드에 이미 있는 위반은 {@link com.tngtech.archunit.library.freeze.FreezingArchRule}로 동결해 두었다
 * ({@code src/test/resources/archunit/}). 동결된 위반은 실패하지 않지만 새 위반은 즉시 실패하고,
 * 위반이 줄어들면 저장소 파일도 같이 줄어든다(ARCH-4 ~ ARCH-9에서 해소).
 *
 * <p>규칙 설명({@code as} · {@code because})은 동결 저장소 {@code stored.rules}의 키가 되므로 영어로 둔다
 * (한글이면 Properties 형식의 유니코드 이스케이프로 저장되어 diff를 읽을 수 없다).
 *
 * <p>테스트 코드는 검사하지 않는다({@link ImportOption.DoNotIncludeTests}).
 * 다른 모듈끼리의 의존이나 다른 모듈의 리스너는 담당자 영역이라 검사하지 않는다.
 */
@AnalyzeClasses(packages = "com.cartmate.cartmate", importOptions = ImportOption.DoNotIncludeTests.class)
class CartOrderModuleBoundaryTest {

    private static final String BOUNDED_CONTEXT = "..boundedContext..";
    private static final String CART = "..boundedContext.cart..";
    private static final String ORDER = "..boundedContext.order..";

    /** cart · order의 내부 패키지. 계약(shared)이 아니므로 다른 모듈이 참조하면 안 된다. */
    private static final String[] CART_ORDER_INTERNALS = {
            "..boundedContext.cart.app..", "..boundedContext.cart.domain..", "..boundedContext.cart.out..",
            "..boundedContext.cart.in..", "..boundedContext.cart.dto..",
            "..boundedContext.order.app..", "..boundedContext.order.domain..", "..boundedContext.order.out..",
            "..boundedContext.order.in..", "..boundedContext.order.dto..",
    };

    // R1. cart ↔ order 직접 의존 금지 (규칙 1: 모듈 간 동기 호출은 포트 경유, 반환은 shared record만)

    @ArchTest
    static final ArchRule r1_cartMustNotDependOnOrder = freeze(
            noClasses().that().resideInAPackage(CART)
                    .should().dependOnClassesThat().resideInAPackage(ORDER)
                    .as("R1 cart must not depend on order")
                    .because("cart and order are separate services; they talk only through shared events and ports (ARCH-1 rule 1)"));

    @ArchTest
    static final ArchRule r1_orderMustNotDependOnCart = freeze(
            noClasses().that().resideInAPackage(ORDER)
                    .should().dependOnClassesThat().resideInAPackage(CART)
                    .as("R1 order must not depend on cart")
                    .because("cart and order are separate services; they talk only through shared events and ports (ARCH-1 rule 1)"));

    // R2. cart · order는 다른 boundedContext를 import하지 않는다 (shared · global · 프레임워크는 허용)

    @ArchTest
    static final ArchRule r2_cartMustNotDependOnOtherBoundedContexts = freeze(
            noClasses().that().resideInAPackage(CART)
                    .should().dependOnClassesThat(resideInAPackage(BOUNDED_CONTEXT).and(not(resideInAPackage(CART))))
                    .as("R2 cart must not depend on other bounded contexts")
                    .because("contracts with other modules live in shared only; no direct use of another module's Facade or entity (ARCH-1 rules 1, 3)"));

    @ArchTest
    static final ArchRule r2_orderMustNotDependOnOtherBoundedContexts = freeze(
            noClasses().that().resideInAPackage(ORDER)
                    .should().dependOnClassesThat(resideInAPackage(BOUNDED_CONTEXT).and(not(resideInAPackage(ORDER))))
                    .as("R2 order must not depend on other bounded contexts")
                    .because("contracts with other modules live in shared only; no direct use of another module's Facade or entity (ARCH-1 rules 1, 3)"));

    // R3. 다른 모듈은 cart · order 내부(app · domain · out · in · dto)에 접근하지 않는다

    @ArchTest
    static final ArchRule r3_otherModulesMustNotDependOnCartOrderInternals = freeze(
            noClasses().that(not(resideInAnyPackage(CART, ORDER)))
                    .should().dependOnClassesThat().resideInAnyPackage(CART_ORDER_INTERNALS)
                    .as("R3 classes outside cart and order must not depend on cart/order internals (app, domain, out, in, dto)")
                    .because("the only contract cart and order expose is the shared package: events, ports, records (ARCH-1 rules 1, 3)"));

    // R4. cart · order 안에서는 같은 TX 리스너(@EventListener) 금지. @TransactionalEventListener(AFTER_COMMIT)만 허용

    @ArchTest
    static final ArchRule r4_cartOrderMustNotUseSameTransactionEventListener = freeze(
            noMethods().that().areDeclaredInClassesThat().resideInAnyPackage(CART, ORDER)
                    .should().beAnnotatedWith(EventListener.class)
                    .as("R4 methods in cart and order must not be annotated with @EventListener (use @TransactionalEventListener AFTER_COMMIT)")
                    .because("cross-module writes happen only after commit; a same-transaction listener lets one module's rollback spread into another (ARCH-1 rule 2)"));
}
