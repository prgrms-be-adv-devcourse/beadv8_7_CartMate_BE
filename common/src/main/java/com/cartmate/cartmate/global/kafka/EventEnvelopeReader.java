package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.global.event.EventEnvelope;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

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
