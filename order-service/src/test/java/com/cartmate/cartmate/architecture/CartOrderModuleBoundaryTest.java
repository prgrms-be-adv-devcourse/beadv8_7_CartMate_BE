package com.cartmate.cartmate.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

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

/**
 * cart · order 모듈 경계 규칙 (ARCH-1 경계 규칙 1~3 · 6).
 *
 * <p>cart와 order는 한 앱 · 한 DB에 있지만 서로 다른 서비스처럼 다룬다.
 * 모듈 간 계약은 {@code com.cartmate.cartmate.shared} 패키지로만 드러낸다.
 * 다른 모듈이 cart · order 안의 어떤 클래스도 참조하지 않고(R3), cart · order도 다른 모듈을 참조하지 않는다(R1 · R2).
 * 예외는 하나: cart · order의 {@code out} 어댑터는 다른 모듈의 {@code app} Facade를 불러도 된다.
 * 어댑터가 나중에 HTTP · Kafka 클라이언트로 바뀌는 교체 지점이기 때문이다(ARCH-15 결정).
 * 그 어댑터도 다른 모듈의 엔티티 · Q클래스 · 리포지토리({@code domain} · {@code out})는 쓰지 못한다(R5).
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
    private static final String CART = "..boundedContext.orderService.cart..";
    private static final String ORDER = "..boundedContext.orderService.order..";

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

    // R2. cart · order는 다른 boundedContext를 참조하지 않는다 (shared · global · 프레임워크는 허용).
    // 예외: 자기 모듈 out 어댑터가 다른 모듈 app(Facade)을 부르는 것. 물리 분리 때 그 어댑터만 HTTP · Kafka로 바꾼다

    @ArchTest
    static final ArchRule r2_cartMustNotDependOnOtherBoundedContexts = freeze(
            classes().that().resideInAPackage(CART)
                    .should(notDependOnOtherBoundedContextsExceptAdapterFacades("orderService.cart"))
                    .as("R2 cart must not depend on other bounded contexts, except cart.out adapters calling their app facades")
                    .because("contracts with other modules live in shared; an out adapter is the one swap point for a later HTTP or Kafka client (ARCH-1 rule 1, ARCH-15)"));

    @ArchTest
    static final ArchRule r2_orderMustNotDependOnOtherBoundedContexts = freeze(
            classes().that().resideInAPackage(ORDER)
                    .should(notDependOnOtherBoundedContextsExceptAdapterFacades("orderService.order"))
                    .as("R2 order must not depend on other bounded contexts, except order.out adapters calling their app facades")
                    .because("contracts with other modules live in shared; an out adapter is the one swap point for a later HTTP or Kafka client (ARCH-1 rule 1, ARCH-15)"));

    // R3. 다른 모듈은 cart · order 안의 어떤 클래스도 참조하지 않는다. 예외 없음 (Facade도 포함, shared 포트를 쓴다)

    @ArchTest
    static final ArchRule r3_otherModulesMustNotDependOnCartOrder = freeze(
            noClasses().that(not(resideInAnyPackage(CART, ORDER)))
                    .should().dependOnClassesThat().resideInAnyPackage(CART, ORDER)
                    .as("R3 classes outside cart and order must not depend on any cart or order class")
                    .because("the only contract cart and order expose is the shared package: events, ports, records (ARCH-1 rules 1, 3, ARCH-15)"));

    // R4. cart · order 안에서는 같은 TX 리스너(@EventListener) 금지. @TransactionalEventListener(AFTER_COMMIT)만 허용

    @ArchTest
    static final ArchRule r4_cartOrderMustNotUseSameTransactionEventListener = freeze(
            noMethods().that().areDeclaredInClassesThat().resideInAnyPackage(CART, ORDER)
                    .should().beAnnotatedWith(EventListener.class)
                    .as("R4 methods in cart and order must not be annotated with @EventListener (use @TransactionalEventListener AFTER_COMMIT)")
                    .because("cross-module writes happen only after commit; a same-transaction listener lets one module's rollback spread into another (ARCH-1 rule 2)"));

    // R5. cart · order는 다른 모듈의 엔티티 · Q클래스 · 리포지토리(domain · out)를 참조하지 않는다. out 어댑터도 마찬가지

    @ArchTest
    static final ArchRule r5_cartMustNotDependOnOtherModulesDomainOrOut = freeze(
            noClasses().that().resideInAPackage(CART)
                    .should().dependOnClassesThat(otherModulesDomainOrOut(CART))
                    .as("R5 cart must not depend on other modules' domain or out packages (entities, Q classes, repositories)")
                    .because("another module's tables belong to its own schema; joins, FKs and locks across schemas are not allowed (team decision 2026-11-04, ARCH-15)"));

    @ArchTest
    static final ArchRule r5_orderMustNotDependOnOtherModulesDomainOrOut = freeze(
            noClasses().that().resideInAPackage(ORDER)
                    .should().dependOnClassesThat(otherModulesDomainOrOut(ORDER))
                    .as("R5 order must not depend on other modules' domain or out packages (entities, Q classes, repositories)")
                    .because("another module's tables belong to its own schema; joins, FKs and locks across schemas are not allowed (team decision 2026-11-04, ARCH-15)"));

    private static DescribedPredicate<JavaClass> otherModulesDomainOrOut(String ownModule) {
        return resideInAnyPackage("..boundedContext.*.*.domain..", "..boundedContext.*.*.out..")
                .and(not(resideInAPackage(ownModule)));
    }

    // 다른 boundedContext로 가는 의존을 하나씩 본다. 자기 모듈 out 어댑터 → 다른 모듈 app의 *Facade 만 통과시킨다.
    // 위반 문구는 ArchUnit 기본 의존 설명을 그대로 써서 동결 저장소가 다른 규칙과 같은 형식이 된다
    private static ArchCondition<JavaClass> notDependOnOtherBoundedContextsExceptAdapterFacades(String module) {
        DescribedPredicate<JavaClass> otherBoundedContext = resideInAPackage(BOUNDED_CONTEXT)
                .and(not(resideInAPackage("..boundedContext." + module + "..")));
        DescribedPredicate<JavaClass> adapter = resideInAPackage("..boundedContext." + module + ".out..");
        DescribedPredicate<JavaClass> facadeOfOtherModule = resideInAPackage("..boundedContext.*.*.app..")
                .and(simpleNameEndingWith("Facade"));
        return new ArchCondition<>("not depend on other bounded contexts, except " + module
                + ".out adapters calling their app facades") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                boolean fromAdapter = adapter.test(origin);
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass().getBaseComponentType();
                    if (!otherBoundedContext.test(target) || (fromAdapter && facadeOfOtherModule.test(target))) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        };
    }
}
