package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.shared.event.DomainEvent;
import com.cartmate.cartmate.shared.event.EventEnvelope;
import com.cartmate.cartmate.shared.event.OutboxMessageRelayed;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 같은 프로세스 안에서 돌리는 싱크: payload를 원래 이벤트로 되돌려 {@link OutboxMessageRelayed}로 다시 발행한다.
 * Kafka가 생기면 이 자리에 Kafka 싱크가 들어가고, consumer는 같은 봉투를 받는다.
 */
@Component
@RequiredArgsConstructor
public class InProcessOutboxRelaySink implements OutboxRelaySink {

    private final DomainEventTypeRegistry typeRegistry;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void send(EventEnvelope envelope) {
        Class<? extends DomainEvent> type = typeRegistry.resolve(envelope.eventType());
        DomainEvent event = objectMapper.readValue(envelope.payload(), type);
        applicationEventPublisher.publishEvent(new OutboxMessageRelayed(envelope, event));
    }
}
