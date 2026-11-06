package com.cartmate.cartmate.global.event;

import java.time.LocalDateTime;

public record EventEnvelope(
        String eventId,
        String eventType,
        String topic,
        String aggregateType,
        String aggregateId,
        LocalDateTime occurredAt,
        int version,
        String payload
) {
}
