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

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "com.cartmate.cartmate", importOptions = ImportOption.DoNotIncludeTests.class)
class AbuseModuleBoundaryTest {

    private static final String BOUNDED_CONTEXT = "..boundedContext..";
    private static final String ABUSE = "..boundedContext.abuse..";
    private static final String ANY_MODULE_OUT = "..boundedContext.*.out..";

    @ArchTest
    static final ArchRule a1_abuseMustNotDependOnOtherBoundedContexts = classes().that().resideInAPackage(ABUSE)
            .should(dependOnlyThroughAdapterFacades(
                    resideInAPackage("..boundedContext.abuse.out.."),
                    resideInAPackage(BOUNDED_CONTEXT).and(not(resideInAPackage(ABUSE))),
                    resideInAPackage("..boundedContext.*.app..").and(simpleNameEndingWith("Facade"))))
            .as("A1 abuse must not depend on other bounded contexts, except abuse.out adapters calling their app facades")
            .because("abuse is a separate service; it talks to other modules only through shared events and out adapters (ABU-14, ARCH-15)");

    @ArchTest
    static final ArchRule a2_otherModulesMustNotDependOnAbuse = classes().that(not(resideInAPackage(ABUSE)))
            .should(dependOnlyThroughAdapterFacades(
                    resideInAPackage(ANY_MODULE_OUT),
                    resideInAPackage(ABUSE),
                    resideInAPackage("..boundedContext.abuse.app..").and(simpleNameEndingWith("Facade"))))
            .as("A2 classes outside abuse must not depend on abuse, except other modules' out adapters calling abuse app facades")
            .because("abuse tables live in their own schema; other modules reach abuse only through an out adapter that can become an HTTP or Kafka client (ABU-14, ARCH-15)");

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
