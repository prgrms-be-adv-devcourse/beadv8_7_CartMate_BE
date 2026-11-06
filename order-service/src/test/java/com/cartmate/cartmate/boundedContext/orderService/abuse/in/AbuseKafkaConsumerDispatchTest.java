package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.kafka.EventEnvelopeReader;
import com.cartmate.cartmate.shared.cart.SharedGroupPurchaseCancelReason;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseCanceledEvent;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseMemberJoinedEvent;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseMemberLeftEvent;
import com.cartmate.cartmate.shared.order.OrderCancelReason;
import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.shared.order.SharedGroupOrderCancelReason;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AbuseKafkaConsumerDispatchTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 6, 12, 0);

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final EventEnvelopeReader reader = new EventEnvelopeReader(objectMapper);

    @Test
    @DisplayName("주문 토픽 메시지는 봉투의 이벤트 종류에 따라 공동 주문 취소 · 주문 취소 처리로 나뉜다")
    void dispatchesOrderEvents() {
        AbuseOrderEventConsumer target = mock(AbuseOrderEventConsumer.class);
        AbuseOrderKafkaConsumer consumer = new AbuseOrderKafkaConsumer(reader, target);
        SharedGroupOrderCanceledEvent sharedCanceled = new SharedGroupOrderCanceledEvent(100L, 10L,
                SharedGroupOrderCancelReason.CREATOR_CANCELED, AT, List.of(2L), 1L);
        OrderCanceledEvent orderCanceled = new OrderCanceledEvent(500L, 7L, OrderType.INDIVIDUAL_GROUP,
                OrderCancelReason.PAYMENT_EXPIRED, false, AT);

        consumer.dispatch(envelope(sharedCanceled));
        consumer.dispatch(envelope(orderCanceled));

        verify(target).accept(sharedCanceled);
        verify(target).accept(orderCanceled);
    }

    @Test
    @DisplayName("cart 토픽 메시지는 참여 · 이탈 · 취소 처리로 나뉘고, 모르는 종류는 무시한다")
    void dispatchesCartEvents() {
        AbuseCartEventConsumer target = mock(AbuseCartEventConsumer.class);
        AbuseCartKafkaConsumer consumer = new AbuseCartKafkaConsumer(reader, target);
        SharedGroupPurchaseMemberJoinedEvent joined = new SharedGroupPurchaseMemberJoinedEvent(10L, 2L, 5, AT);
        SharedGroupPurchaseMemberLeftEvent left = new SharedGroupPurchaseMemberLeftEvent(10L, 2L, AT.minusHours(1), AT);
        SharedGroupPurchaseCanceledEvent canceled = new SharedGroupPurchaseCanceledEvent(10L, 1L, List.of(1L, 2L),
                SharedGroupPurchaseCancelReason.CREATOR_CANCELED, AT);

        consumer.dispatch(envelope(joined));
        consumer.dispatch(envelope(left));
        consumer.dispatch(envelope(canceled));

        verify(target).accept(joined);
        verify(target).accept(left);
        verify(target).accept(canceled);
    }

    @Test
    @DisplayName("받기로 하지 않은 이벤트 종류는 아무 처리도 하지 않는다")
    void ignoresUnknownType() {
        AbuseCartEventConsumer target = mock(AbuseCartEventConsumer.class);

        new AbuseCartKafkaConsumer(reader, target).dispatch(
                new EventEnvelope("id", "UnknownEvent", "topic", "X", "1", AT, 1, "{}"));

        verifyNoInteractions(target);
    }

    private EventEnvelope envelope(DomainEvent event) {
        return new EventEnvelope("id", event.getClass().getSimpleName(), "topic", event.aggregateType(),
                event.aggregateId(), AT, 1, objectMapper.writeValueAsString(event));
    }
}
