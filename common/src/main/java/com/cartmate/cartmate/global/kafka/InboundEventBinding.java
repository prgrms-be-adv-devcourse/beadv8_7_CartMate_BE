package com.cartmate.cartmate.global.kafka;

import java.time.Duration;

public record InboundEventBinding(String topic, Class<?> eventType, Duration retryTimeout) {

    public InboundEventBinding(String topic, Class<?> eventType) {
        this(topic, eventType, null);
    }
}
