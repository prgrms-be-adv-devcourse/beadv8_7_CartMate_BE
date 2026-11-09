package com.cartmate.cartmate.support.context;

import com.cartmate.cartmate.support.CartMateIntegrationTest;
import com.cartmate.cartmate.support.CartMateJpaTest;
import com.cartmate.cartmate.support.CartMateKafkaIntegrationTest;
import org.springframework.core.SpringProperties;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.test.context.BootstrapUtils;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.bean.override.BeanOverrideHandler;
import org.springframework.test.context.cache.ContextCache;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class ContextUsageReport {

    static final Path OUTPUT = Path.of("build", "reports", "test-contexts.md");
    static final int CANDIDATE_THRESHOLD = 2;

    private static final Pattern SHORT_PACKAGE = Pattern.compile("\\b[a-z][\\w]*(\\.[a-z][\\w]*)*\\.(?=[A-Z])");
    private static final Map<MergedContextConfiguration, Set<String>> USAGES = new LinkedHashMap<>();
    private static List<Shared> baselines;

    private ContextUsageReport() {
    }

    record Shared(String name, Class<? extends Annotation> annotation, MergedContextConfiguration config) {
    }

    static synchronized void record(MergedContextConfiguration config, Class<?> testClass) {
        USAGES.computeIfAbsent(config, key -> new TreeSet<>()).add(ClassUtils.getShortName(testClass));
        write();
    }

    private static List<Shared> baselines() {
        if (baselines == null) {
            baselines = List.of(
                    shared("공용 통합", CartMateIntegrationTest.class, SharedContextBaselines.Integration.class),
                    shared("공용 JPA 슬라이스", CartMateJpaTest.class, SharedContextBaselines.Jpa.class),
                    shared("공용 Kafka 통합", CartMateKafkaIntegrationTest.class, SharedContextBaselines.Kafka.class));
        }
        return baselines;
    }

    private static Shared shared(String name, Class<? extends Annotation> annotation, Class<?> baselineClass) {
        MergedContextConfiguration config = BootstrapUtils.resolveTestContextBootstrapper(baselineClass)
                .buildMergedContextConfiguration();
        return new Shared(name, annotation, config);
    }

    private static void write() {
        try {
            Files.createDirectories(OUTPUT.getParent());
            Files.writeString(OUTPUT, render());
        } catch (IOException | RuntimeException e) {
            System.err.println("[test-contexts] " + OUTPUT + " 을 쓰지 못했다: " + e);
        }
    }

    static String render() {
        List<Shared> shared = baselines();
        List<String> sharedSections = new ArrayList<>();
        List<String> candidateSections = new ArrayList<>();
        for (Map.Entry<MergedContextConfiguration, Set<String>> usage : USAGES.entrySet()) {
            MergedContextConfiguration config = usage.getKey();
            Shared match = shared.stream().filter(s -> s.config().equals(config)).findFirst().orElse(null);
            if (match != null) {
                sharedSections.add(section(match.name(), usage.getValue(), config, null));
            } else {
                Shared base = closestBaseline(shared, config);
                candidateSections.add(section("통합 후보", usage.getValue(), config, base));
            }
        }

        StringBuilder out = new StringBuilder("# 테스트 컨텍스트 (order-service)\n\n");
        if (candidateSections.size() >= CANDIDATE_THRESHOLD) {
            out.append("> **통합 필요**: 공용 어노테이션 밖에서 생긴 컨텍스트(통합 후보)가 ").append(candidateSections.size())
                    .append("개다(기준 ").append(CANDIDATE_THRESHOLD).append("개 이상). 아래 차이를 없애 공용 컨텍스트로 합친다. ")
                    .append("테스트는 실패시키지 않는다.\n\n");
        }
        String cacheMax = Objects.requireNonNullElse(
                SpringProperties.getProperty(ContextCache.MAX_CONTEXT_CACHE_SIZE_PROPERTY_NAME),
                ContextCache.DEFAULT_MAX_CONTEXT_CACHE_SIZE + " (기본값)");
        out.append("- 컨텍스트 ").append(USAGES.size()).append("개: 공용 ").append(sharedSections.size())
                .append(" · 통합 후보 ").append(candidateSections.size()).append('\n');
        out.append("- 컨텍스트 캐시 상한: ").append(cacheMax)
                .append(" (넘으면 가장 오래 안 쓴 컨텍스트를 닫고 다시 띄운다)\n");
        out.append("- 공용: `@CartMateIntegrationTest` · `@CartMateJpaTest` · `@CartMateKafkaIntegrationTest`만 붙여 만든 구성과 같은 컨텍스트. ")
                .append("공용 어노테이션을 붙였더라도 @MockitoBean · 속성 · @Import를 더하면 통합 후보가 된다\n\n");
        sharedSections.forEach(out::append);
        candidateSections.forEach(out::append);
        return out.toString();
    }

    private static Shared closestBaseline(List<Shared> shared, MergedContextConfiguration config) {
        Class<?> testClass = config.getTestClass();
        for (Shared candidate : shared) {
            for (Class<?> type = testClass; type != null; type = type.getEnclosingClass()) {
                if (MergedAnnotations.from(type, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY).isPresent(candidate.annotation())) {
                    return candidate;
                }
            }
        }
        return shared.getFirst();
    }

    private static String section(String title, Set<String> testClasses, MergedContextConfiguration config, Shared base) {
        StringBuilder out = new StringBuilder("## ").append(title).append(" — 테스트 클래스 ").append(testClasses.size()).append("개\n\n");
        out.append("- 테스트 클래스: ").append(String.join(", ", testClasses)).append('\n');
        out.append("- 설정 클래스: ").append(join(simpleNames(Arrays.asList(config.getClasses())))).append('\n');
        out.append("- 프로필: ").append(join(Arrays.asList(config.getActiveProfiles()))).append('\n');
        out.append("- 속성: ").append(join(Arrays.stream(config.getPropertySourceProperties())
                .filter(property -> !property.endsWith("ContextBootstrapper=true")).toList())).append('\n');
        List<String> overrides = beanOverrides(config.getContextCustomizers());
        if (!overrides.isEmpty()) {
            out.append("- mock/spy 빈: ").append(join(overrides)).append('\n');
        }
        if (base != null) {
            out.append("- **").append(base.name()).append(" 컨텍스트와 다른 점**:\n");
            List<String> differences = differences(base.config(), config);
            if (differences.isEmpty()) {
                out.append("  - (구성 요소 차이를 찾지 못했다. 부모 컨텍스트 · 로더 차이일 수 있다)\n");
            }
            differences.forEach(line -> out.append("  - ").append(line).append('\n'));
        }
        return out.append('\n').toString();
    }

    private static List<String> differences(MergedContextConfiguration base, MergedContextConfiguration config) {
        List<String> lines = new ArrayList<>();
        diff(lines, "설정 클래스", simpleNames(Arrays.asList(base.getClasses())), simpleNames(Arrays.asList(config.getClasses())));
        diff(lines, "프로필", Arrays.asList(base.getActiveProfiles()), Arrays.asList(config.getActiveProfiles()));
        diff(lines, "속성", Arrays.asList(base.getPropertySourceProperties()), Arrays.asList(config.getPropertySourceProperties()));
        diff(lines, "속성 파일", propertyFiles(base), propertyFiles(config));
        diff(lines, "초기화 클래스", simpleNames(base.getContextInitializerClasses()), simpleNames(config.getContextInitializerClasses()));
        Set<ContextCustomizer> added = new LinkedHashSet<>(config.getContextCustomizers());
        added.removeAll(base.getContextCustomizers());
        Set<ContextCustomizer> removed = new LinkedHashSet<>(base.getContextCustomizers());
        removed.removeAll(config.getContextCustomizers());
        ContextCustomizer addedImports = takeImports(added);
        ContextCustomizer removedImports = takeImports(removed);
        if (addedImports != null && removedImports != null) {
            diff(lines, "테스트 클래스 어노테이션 · @Import", importKey(removedImports), importKey(addedImports));
        } else {
            if (addedImports != null) {
                added.add(addedImports);
            }
            if (removedImports != null) {
                removed.add(removedImports);
            }
        }
        added.forEach(customizer -> lines.add("추가된 커스터마이저: " + describe(customizer)));
        removed.forEach(customizer -> lines.add("빠진 커스터마이저: " + describe(customizer)));
        if (!Objects.equals(base.getContextLoader().getClass(), config.getContextLoader().getClass())) {
            lines.add("컨텍스트 로더: " + config.getContextLoader().getClass().getSimpleName());
        }
        if (config.getParent() != null) {
            lines.add("부모 컨텍스트가 있다(@ContextHierarchy)");
        }
        return lines;
    }

    private static List<String> propertyFiles(MergedContextConfiguration config) {
        return config.getPropertySourceDescriptors().stream()
                .flatMap(descriptor -> descriptor.locations().stream())
                .collect(Collectors.toList());
    }

    private static void diff(List<String> lines, String label, Collection<String> base, Collection<String> actual) {
        Set<String> added = new LinkedHashSet<>(actual);
        added.removeAll(base);
        Set<String> removed = new LinkedHashSet<>(base);
        removed.removeAll(actual);
        if (!added.isEmpty()) {
            lines.add(label + " 추가: " + join(added));
        }
        if (!removed.isEmpty()) {
            lines.add(label + " 빠짐: " + join(removed));
        }
    }

    private static ContextCustomizer takeImports(Set<ContextCustomizer> customizers) {
        ContextCustomizer found = customizers.stream()
                .filter(customizer -> importKey(customizer) != null)
                .findFirst().orElse(null);
        customizers.remove(found);
        return found;
    }

    private static Set<String> importKey(ContextCustomizer customizer) {
        Object key = fieldValue(customizer, "key");
        if (key == null || !(fieldValue(key, "key") instanceof Collection<?> annotations)) {
            return null;
        }
        return annotations.stream()
                .map(annotation -> SHORT_PACKAGE.matcher(String.valueOf(annotation)).replaceAll(""))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Object fieldValue(Object target, String name) {
        Field field = ReflectionUtils.findField(target.getClass(), name);
        if (field == null) {
            return null;
        }
        ReflectionUtils.makeAccessible(field);
        return ReflectionUtils.getField(field, target);
    }

    private static String describe(ContextCustomizer customizer) {
        List<String> overrides = beanOverrides(List.of(customizer));
        if (!overrides.isEmpty()) {
            return "mock/spy 빈 " + join(overrides);
        }
        Method toString = ReflectionUtils.findMethod(customizer.getClass(), "toString");
        if (toString != null && toString.getDeclaringClass() != Object.class) {
            return customizer.getClass().getSimpleName() + " " + customizer;
        }
        return customizer.getClass().getSimpleName();
    }

    private static List<String> beanOverrides(Collection<ContextCustomizer> customizers) {
        List<String> lines = new ArrayList<>();
        for (ContextCustomizer customizer : customizers) {
            Field field = ReflectionUtils.findField(customizer.getClass(), "handlers");
            if (field == null) {
                continue;
            }
            ReflectionUtils.makeAccessible(field);
            if (ReflectionUtils.getField(field, customizer) instanceof Collection<?> handlers) {
                for (Object handler : handlers) {
                    if (handler instanceof BeanOverrideHandler override) {
                        lines.add(describe(override));
                    }
                }
            }
        }
        return lines;
    }

    private static String describe(BeanOverrideHandler handler) {
        String kind = switch (handler.getClass().getSimpleName()) {
            case "MockitoBeanOverrideHandler" -> "@MockitoBean";
            case "MockitoSpyBeanOverrideHandler" -> "@MockitoSpyBean";
            default -> handler.getClass().getSimpleName();
        };
        String type = handler.getBeanType().toClass().getSimpleName();
        Field field = handler.getField();
        String where = field == null ? "" : " (" + ClassUtils.getShortName(field.getDeclaringClass()) + "." + field.getName() + ")";
        return kind + " " + type + where;
    }

    private static List<String> simpleNames(Collection<? extends Class<?>> classes) {
        return classes.stream().map(ClassUtils::getShortName).collect(Collectors.toList());
    }

    private static String join(Collection<String> values) {
        return values.isEmpty() ? "(없음)" : values.stream().map(value -> "`" + value + "`").collect(Collectors.joining(", "));
    }
}
