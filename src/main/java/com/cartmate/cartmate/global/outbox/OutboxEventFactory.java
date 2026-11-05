package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.shared.event.DomainEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 도메인 이벤트를 아웃박스 봉투({@link OutboxEvent})로 만든다.
 * eventId는 UUID, occurredAt은 지금, version은 {@link OutboxEvent#SCHEMA_VERSION}, payload는 Jackson JSON.
 */
@Component
public class OutboxEventFactory {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public OutboxEventFactory(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemDefaultZone());
    }

    OutboxEventFactory(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public OutboxEvent create(DomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("이벤트가 null이다");
        }
        String aggregateType = event.aggregateType();
        String aggregateId = event.aggregateId();
        if (aggregateType == null || aggregateType.isBlank() || aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException(
                    "aggregateType · aggregateId가 비어 있다: " + event.getClass().getSimpleName());
        }
        return OutboxEvent.create(
                UUID.randomUUID().toString(),
                event.getClass().getSimpleName(),
                OutboxTopics.of(event.getClass()),
                aggregateType,
                aggregateId,
                LocalDateTime.now(clock),
                OutboxEvent.SCHEMA_VERSION,
                objectMapper.writeValueAsString(event)
        );
    }
}
