package com.cartmate.cartmate.boundedContext.paymentService.payout.in;

import com.cartmate.cartmate.boundedContext.paymentService.payout.app.PayoutFacade;
import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.inbox.ProcessedEvents;
import com.cartmate.cartmate.global.kafka.EventEnvelopeReader;
import com.cartmate.cartmate.global.kafka.InboundEvents;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PayoutDeliveryKafkaConsumerTest {

    private static final String TOPIC = "cartmate.delivery.delivery-confirmed";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final PayoutFacade payoutFacade = mock(PayoutFacade.class);
    private final ProcessedEvents processedEvents = mock(ProcessedEvents.class);
    private final InboundEvents inboundEvents = new InboundEvents(
            new EventEnvelopeReader(mapper), processedEvents, new TransactionTemplate(mock(PlatformTransactionManager.class)));
    private final PayoutDeliveryKafkaConsumer consumer = new PayoutDeliveryKafkaConsumer(payoutFacade, inboundEvents);

    private final DeliveryConfirmedEvent event = new DeliveryConfirmedEvent(
            3L, 5L, 100L, PurchaseType.INDIVIDUAL_GROUP,
            LocalDateTime.of(2026, 11, 1, 10, 0), LocalDateTime.of(2026, 11, 8, 10, 0),
            List.of(new DeliveryConfirmedEvent.Item(11L, "상품", 1_000L, 2L)));

    private ConsumerRecord<String, String> record() {
        EventEnvelope envelope = new EventEnvelope("evt-1", "DeliveryConfirmedEvent", TOPIC, "Delivery", "3",
                LocalDateTime.of(2026, 11, 8, 10, 0), 1, mapper.writeValueAsString(event));
        return new ConsumerRecord<>(TOPIC, 0, 0L, "3", mapper.writeValueAsString(envelope));
    }

    @Test
    @DisplayName("처음 받은 구매확정 이벤트는 처리 기록을 남기고 정산을 만든다")
    void createsPayoutsOnFirstDelivery() {
        when(processedEvents.markFirst("kafka:" + TOPIC, "evt-1")).thenReturn(true);

        consumer.onMessage(record());

        verify(payoutFacade).createPayouts(event);
    }

    @Test
    @DisplayName("이미 처리한 이벤트는 정산을 다시 만들지 않는다")
    void skipsAlreadyProcessedEvent() {
        when(processedEvents.markFirst("kafka:" + TOPIC, "evt-1")).thenReturn(false);

        consumer.onMessage(record());

        verifyNoInteractions(payoutFacade);
    }

    @Test
    @DisplayName("정산 생성이 실패하면 예외를 올려 재시도하게 한다")
    void rethrowsWhenPayoutCreationFails() {
        when(processedEvents.markFirst("kafka:" + TOPIC, "evt-1")).thenReturn(true);
        doThrow(new IllegalStateException("db down")).when(payoutFacade).createPayouts(event);

        assertThatThrownBy(() -> consumer.onMessage(record())).isInstanceOf(IllegalStateException.class);
    }
}
