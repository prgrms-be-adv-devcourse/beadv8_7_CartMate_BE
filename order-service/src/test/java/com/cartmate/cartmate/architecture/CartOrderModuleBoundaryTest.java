package com.cartmate.cartmate.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

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

@AnalyzeClasses(packages = "com.cartmate.cartmate", importOptions = ImportOption.DoNotIncludeTests.class)
class CartOrderModuleBoundaryTest {

    private static final String BOUNDED_CONTEXT = "..boundedContext..";
    private static final String CART = "..boundedContext.orderService.cart..";
    private static final String ORDER = "..boundedContext.orderService.order..";

    @ArchTest
    static final ArchRule r1_cartMustNotDependOnOrder =
            noClasses().that().resideInAPackage(CART)
                    .should().dependOnClassesThat().resideInAPackage(ORDER)
                    .as("R1 cart must not depend on order")
                    .because("cart and order are separate services; they talk only through shared events and ports (ARCH-1 rule 1)");

    @ArchTest
    static final ArchRule r1_orderMustNotDependOnCart =
            noClasses().that().resideInAPackage(ORDER)
                    .should().dependOnClassesThat().resideInAPackage(CART)
                    .as("R1 order must not depend on cart")
                    .because("cart and order are separate services; they talk only through shared events and ports (ARCH-1 rule 1)");

    @ArchTest
    static final ArchRule r2_cartMustNotDependOnOtherBoundedContexts =
            classes().that().resideInAPackage(CART)
                    .should(notDependOnOtherBoundedContextsExceptAdapterFacades("orderService.cart"))
                    .as("R2 cart must not depend on other bounded contexts, except cart.out adapters calling their app facades")
                    .because("contracts with other modules live in shared; an out adapter is the one swap point for a later HTTP or Kafka client (ARCH-1 rule 1, ARCH-15)");

    @ArchTest
    static final ArchRule r2_orderMustNotDependOnOtherBoundedContexts =
            classes().that().resideInAPackage(ORDER)
                    .should(notDependOnOtherBoundedContextsExceptAdapterFacades("orderService.order"))
                    .as("R2 order must not depend on other bounded contexts, except order.out adapters calling their app facades")
                    .because("contracts with other modules live in shared; an out adapter is the one swap point for a later HTTP or Kafka client (ARCH-1 rule 1, ARCH-15)");

    @ArchTest
    static final ArchRule r3_otherModulesMustNotDependOnCartOrder =
            noClasses().that(not(resideInAnyPackage(CART, ORDER)))
                    .should().dependOnClassesThat().resideInAnyPackage(CART, ORDER)
                    .as("R3 classes outside cart and order must not depend on any cart or order class")
                    .because("the only contract cart and order expose is the shared package: events, ports, records (ARCH-1 rules 1, 3, ARCH-15)");

    @ArchTest
    static final ArchRule r4_cartOrderMustNotUseSameTransactionEventListener =
            noMethods().that().areDeclaredInClassesThat().resideInAnyPackage(CART, ORDER)
                    .should().beAnnotatedWith(EventListener.class)
                    .as("R4 methods in cart and order must not be annotated with @EventListener (use @TransactionalEventListener AFTER_COMMIT)")
                    .because("cross-module writes happen only after commit; a same-transaction listener lets one module's rollback spread into another (ARCH-1 rule 2)");

    @ArchTest
    static final ArchRule r5_cartMustNotDependOnOtherModulesDomainOrOut =
            noClasses().that().resideInAPackage(CART)
                    .should().dependOnClassesThat(otherModulesDomainOrOut(CART))
                    .as("R5 cart must not depend on other modules' domain or out packages (entities, Q classes, repositories)")
                    .because("another module's tables belong to its own schema; joins, FKs and locks across schemas are not allowed (team decision 2026-11-04, ARCH-15)");

    @ArchTest
    static final ArchRule r5_orderMustNotDependOnOtherModulesDomainOrOut =
            noClasses().that().resideInAPackage(ORDER)
                    .should().dependOnClassesThat(otherModulesDomainOrOut(ORDER))
                    .as("R5 order must not depend on other modules' domain or out packages (entities, Q classes, repositories)")
                    .because("another module's tables belong to its own schema; joins, FKs and locks across schemas are not allowed (team decision 2026-11-04, ARCH-15)");

    private static DescribedPredicate<JavaClass> otherModulesDomainOrOut(String ownModule) {
        return resideInAnyPackage("..boundedContext.*.*.domain..", "..boundedContext.*.*.out..")
                .and(not(resideInAPackage(ownModule)));
    }

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
