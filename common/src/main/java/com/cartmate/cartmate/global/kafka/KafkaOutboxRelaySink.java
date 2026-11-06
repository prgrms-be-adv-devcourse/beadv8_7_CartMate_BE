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

/**
 * outbox 행을 Kafka로 내보내는 싱크. 릴레이가 행마다 부르고, 브로커가 받았다고 답할 때까지 기다린다(동기).
 * 실패하면 예외를 그대로 올려 릴레이가 백오프 후 다시 보낸다(at-least-once). 그래서 받는 쪽은 eventId로 중복을 걸러야 한다.
 *
 * <p>메시지 모양(서비스 사이 계약):
 * <ul>
 *   <li>토픽: {@link EventEnvelope#topic()} — {@code cartmate.<모듈>.<이벤트-kebab>}</li>
 *   <li>키: aggregateId — 같은 주문 · 공동구매의 이벤트는 같은 파티션에 들어가 순서가 지켜진다</li>
 *   <li>값: {@link EventEnvelope} JSON 전체(payload는 원래 이벤트 JSON 문자열)</li>
 *   <li>헤더: {@value #HEADER_EVENT_ID}, {@value #HEADER_EVENT_TYPE} — 값을 풀지 않고도 거르거나 로그를 남길 수 있게</li>
 * </ul>
 */
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
            // 브로커 장애 · 시간 초과는 다시 보내면 될 수 있다 → 릴레이 재시도
            throw new IllegalStateException("Kafka 전송 실패: topic=" + envelope.topic() + ", eventId=" + envelope.eventId(), e);
        }
    }

    // 봉투를 JSON으로 못 바꾸면 다시 해도 같다 → 바로 FAILED
    private String serialize(EventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JacksonException e) {
            throw new NonRetryableRelayException("봉투를 직렬화할 수 없다: eventId=" + envelope.eventId(), e);
        }
    }
}
