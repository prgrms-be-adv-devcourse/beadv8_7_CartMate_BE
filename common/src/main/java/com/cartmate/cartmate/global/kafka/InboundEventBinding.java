package com.cartmate.cartmate.global.kafka;

public record InboundEventBinding(String topic, Class<?> eventType) {
}
