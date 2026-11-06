package com.cartmate.cartmate.global.kafka;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record InboundEventTypes(Map<String, Class<?>> typeByTopic) {

    public static InboundEventTypes of(List<InboundEventBinding> bindings) {
        return new InboundEventTypes(bindings.stream()
                .collect(Collectors.toUnmodifiableMap(InboundEventBinding::topic, InboundEventBinding::eventType)));
    }

    public Optional<Class<?>> typeOf(String topic) {
        return Optional.ofNullable(typeByTopic.get(topic));
    }

    public Set<String> topics() {
        return typeByTopic.keySet();
    }
}
