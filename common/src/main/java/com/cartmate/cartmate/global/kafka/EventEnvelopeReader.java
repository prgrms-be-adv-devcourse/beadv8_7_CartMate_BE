package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.global.event.EventEnvelope;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Kafka 메시지 값(EventEnvelope JSON)을 읽는다. 수신 다리와 서비스별 @KafkaListener가 같은 방식으로 해석하게 한 곳에 둔다.
 * JSON이 깨졌거나 payload가 이벤트 모양과 다르면 JacksonException이 그대로 올라간다 → 공용 에러 핸들러가 재시도 없이 DLT로 보낸다.
 */
@Component
public class EventEnvelopeReader {

    private final ObjectMapper objectMapper;

    public EventEnvelopeReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EventEnvelope envelope(String value) {
        return objectMapper.readValue(value, EventEnvelope.class);
    }

    public <T> T payload(EventEnvelope envelope, Class<T> eventType) {
        return objectMapper.readValue(envelope.payload(), eventType);
    }
}
