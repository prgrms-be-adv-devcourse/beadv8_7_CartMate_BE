package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.outbox.InProcessOutboxRelaySink;
import com.cartmate.cartmate.global.outbox.NonRetryableRelayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CompositeOutboxRelaySinkTest {

    private static final EventEnvelope ENVELOPE = new EventEnvelope("event-1", "OrderPaidEvent",
            "cartmate.order.order-paid", "Order", "1", LocalDateTime.of(2026, 11, 6, 10, 0), 1, "{}");

    private final InProcessOutboxRelaySink inProcess = mock(InProcessOutboxRelaySink.class);
    private final KafkaOutboxRelaySink kafka = mock(KafkaOutboxRelaySink.class);
    private final CompositeOutboxRelaySink sink = new CompositeOutboxRelaySink(inProcess, kafka);

    @Test
    @DisplayName("서비스 안 전달이 실패하면 Kafka로 보내지 않고 실패를 그대로 릴레이에 돌려준다")
    void inProcessFailureSkipsKafka() {
        NonRetryableRelayException failure = new NonRetryableRelayException("되돌릴 수 없는 봉투", null);
        doThrow(failure).when(inProcess).send(ENVELOPE);

        assertThatThrownBy(() -> sink.send(ENVELOPE)).isSameAs(failure);
        verify(kafka, never()).send(any());
    }

    @Test
    @DisplayName("Kafka 전송이 실패하면 예외를 던져 아웃박스 행이 PUBLISHED가 되지 않게 한다")
    void kafkaFailurePropagates() {
        IllegalStateException failure = new IllegalStateException("Kafka 전송 실패");
        doThrow(failure).when(kafka).send(ENVELOPE);

        assertThatThrownBy(() -> sink.send(ENVELOPE)).isSameAs(failure);
    }
}
