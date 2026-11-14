package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.global.event.DomainEvent;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class DomainEventTypeRegistry {

    private final Map<String, Class<? extends DomainEvent>> types;

    public DomainEventTypeRegistry() {
        this(OutboxTopics.SHARED_PACKAGE);
    }

    DomainEventTypeRegistry(String basePackage) {
        this.types = Collections.unmodifiableMap(scan(basePackage));
    }

    public Class<? extends DomainEvent> resolve(String eventType) {
        Class<? extends DomainEvent> type = types.get(eventType);
        if (type == null) {
            throw new IllegalArgumentException("알 수 없는 이벤트 타입: " + eventType);
        }
        return type;
    }

    public Map<String, Class<? extends DomainEvent>> types() {
        return types;
    }

    private static Map<String, Class<? extends DomainEvent>> scan(String basePackage) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(DomainEvent.class));
        Map<String, Class<? extends DomainEvent>> found = new HashMap<>();
        scanner.findCandidateComponents(basePackage).forEach(definition -> {
            Class<? extends DomainEvent> type = load(definition.getBeanClassName());
            Class<? extends DomainEvent> duplicate = found.putIfAbsent(type.getSimpleName(), type);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "이벤트 단순 이름이 겹친다: " + duplicate.getName() + ", " + type.getName());
            }
        });
        return found;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends DomainEvent> load(String className) {
        try {
            return (Class<? extends DomainEvent>) ClassUtils.forName(className, DomainEventTypeRegistry.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("이벤트 클래스를 읽을 수 없다: " + className, e);
        }
    }
}
