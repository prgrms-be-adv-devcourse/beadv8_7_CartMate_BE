package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.observability.TraceparentSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class OutboxEventFactory {

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TraceparentSupport traceparentSupport;

    @Autowired
    public OutboxEventFactory(ObjectMapper objectMapper, TraceparentSupport traceparentSupport) {
        this(objectMapper, Clock.systemDefaultZone(), traceparentSupport);
    }

    OutboxEventFactory(ObjectMapper objectMapper, Clock clock, TraceparentSupport traceparentSupport) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.traceparentSupport = traceparentSupport;
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
                objectMapper.writeValueAsString(event),
                traceparentSupport.current()
        );
    }
}
