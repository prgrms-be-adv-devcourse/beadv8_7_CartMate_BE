package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
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
        applicationEventPublisher.publishEvent(new OutboxMessageRelayed(envelope, deserialize(envelope)));
    }

    // 여기서 나는 실패는 다시 해도 같다. consumer에서 나는 예외는 감싸지 않고 그대로 올려 재시도한다
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
