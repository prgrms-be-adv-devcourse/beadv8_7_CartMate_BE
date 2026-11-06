package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class InProcessOutboxRelaySink implements OutboxRelaySink {

    private final DomainEventTypeRegistry typeRegistry;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void send(EventEnvelope envelope) {
        applicationEventPublisher.publishEvent(new OutboxMessageRelayed(envelope, deserialize(envelope)));
    }

    private DomainEvent deserialize(EventEnvelope envelope) {
        try {
            Class<? extends DomainEvent> type = typeRegistry.resolve(envelope.eventType());
            return objectMapper.readValue(envelope.payload(), type);
        } catch (IllegalArgumentException | JacksonException e) {
            throw new NonRetryableRelayException(
                    "이벤트를 되돌릴 수 없다: type=" + envelope.eventType() + ", eventId=" + envelope.eventId(), e);
        }
    }
}
