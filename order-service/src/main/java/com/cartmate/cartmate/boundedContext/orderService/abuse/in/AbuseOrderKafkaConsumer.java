package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.kafka.EventEnvelopeReader;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cartmate.kafka.enabled", havingValue = "true")
public class AbuseOrderKafkaConsumer {

    static final String SHARED_GROUP_ORDER_CANCELED_TOPIC = "#{T(com.cartmate.cartmate.global.outbox.OutboxTopics)"
            + ".of(T(com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent))}";
    static final String ORDER_CANCELED_TOPIC = "#{T(com.cartmate.cartmate.global.outbox.OutboxTopics)"
            + ".of(T(com.cartmate.cartmate.shared.order.event.OrderCanceledEvent))}";
    static final String GROUP_ID = "order-service-abuse";

    private final EventEnvelopeReader envelopeReader;
    private final AbuseOrderEventConsumer abuseOrderEventConsumer;

    @KafkaListener(topics = {SHARED_GROUP_ORDER_CANCELED_TOPIC, ORDER_CANCELED_TOPIC}, groupId = GROUP_ID)
    public void onMessage(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = envelopeReader.envelope(record.value());
        log.debug("어뷰징 신호 수신: eventId={}, eventType={}", envelope.eventId(), envelope.eventType());
        dispatch(envelope);
    }

    void dispatch(EventEnvelope envelope) {
        String eventType = envelope.eventType();
        if (SharedGroupOrderCanceledEvent.class.getSimpleName().equals(eventType)) {
            abuseOrderEventConsumer.accept(envelopeReader.payload(envelope, SharedGroupOrderCanceledEvent.class));
        } else if (OrderCanceledEvent.class.getSimpleName().equals(eventType)) {
            abuseOrderEventConsumer.accept(envelopeReader.payload(envelope, OrderCanceledEvent.class));
        } else {
            log.debug("어뷰징이 받지 않는 이벤트 종류라 건너뜀: eventId={}, eventType={}", envelope.eventId(), eventType);
        }
    }
}
