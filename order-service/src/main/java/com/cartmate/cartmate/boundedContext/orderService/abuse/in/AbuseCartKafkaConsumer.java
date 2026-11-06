package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.kafka.EventEnvelopeReader;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseCanceledEvent;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseMemberJoinedEvent;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseMemberLeftEvent;
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
public class AbuseCartKafkaConsumer {

    static final String JOINED_TOPIC = "#{T(com.cartmate.cartmate.global.outbox.OutboxTopics)"
            + ".of(T(com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseMemberJoinedEvent))}";
    static final String LEFT_TOPIC = "#{T(com.cartmate.cartmate.global.outbox.OutboxTopics)"
            + ".of(T(com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseMemberLeftEvent))}";
    static final String CANCELED_TOPIC = "#{T(com.cartmate.cartmate.global.outbox.OutboxTopics)"
            + ".of(T(com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseCanceledEvent))}";
    static final String GROUP_ID = "order-service-abuse-cart";

    private final EventEnvelopeReader envelopeReader;
    private final AbuseCartEventConsumer abuseCartEventConsumer;

    @KafkaListener(topics = {JOINED_TOPIC, LEFT_TOPIC, CANCELED_TOPIC}, groupId = GROUP_ID)
    public void onMessage(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = envelopeReader.envelope(record.value());
        log.debug("어뷰징 신호 수신: eventId={}, eventType={}", envelope.eventId(), envelope.eventType());
        dispatch(envelope);
    }

    void dispatch(EventEnvelope envelope) {
        String eventType = envelope.eventType();
        if (SharedGroupPurchaseMemberJoinedEvent.class.getSimpleName().equals(eventType)) {
            abuseCartEventConsumer.accept(envelopeReader.payload(envelope, SharedGroupPurchaseMemberJoinedEvent.class));
        } else if (SharedGroupPurchaseMemberLeftEvent.class.getSimpleName().equals(eventType)) {
            abuseCartEventConsumer.accept(envelopeReader.payload(envelope, SharedGroupPurchaseMemberLeftEvent.class));
        } else if (SharedGroupPurchaseCanceledEvent.class.getSimpleName().equals(eventType)) {
            abuseCartEventConsumer.accept(envelopeReader.payload(envelope, SharedGroupPurchaseCanceledEvent.class));
        } else {
            log.debug("어뷰징이 받지 않는 이벤트 종류라 건너뜀: eventId={}, eventType={}", envelope.eventId(), eventType);
        }
    }
}
