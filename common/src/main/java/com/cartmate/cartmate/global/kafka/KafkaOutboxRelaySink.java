package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.outbox.NonRetryableRelayException;
import com.cartmate.cartmate.global.outbox.OutboxRelaySink;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class KafkaOutboxRelaySink implements OutboxRelaySink {

    public static final String HEADER_EVENT_ID = "eventId";
    public static final String HEADER_EVENT_TYPE = "eventType";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Duration sendTimeout;

    public KafkaOutboxRelaySink(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, Duration sendTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.sendTimeout = sendTimeout;
    }

    @Override
    public void send(EventEnvelope envelope) {
        ProducerRecord<String, String> record = new ProducerRecord<>(envelope.topic(), envelope.aggregateId(), serialize(envelope));
        record.headers().add(HEADER_EVENT_ID, envelope.eventId().getBytes(StandardCharsets.UTF_8));
        record.headers().add(HEADER_EVENT_TYPE, envelope.eventType().getBytes(StandardCharsets.UTF_8));
        try {
            kafkaTemplate.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka 전송 중 중단됨: eventId=" + envelope.eventId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka 전송 실패: topic=" + envelope.topic() + ", eventId=" + envelope.eventId(), e);
        }
    }

    private String serialize(EventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JacksonException e) {
            throw new NonRetryableRelayException("봉투를 직렬화할 수 없다: eventId=" + envelope.eventId(), e);
        }
    }
}
