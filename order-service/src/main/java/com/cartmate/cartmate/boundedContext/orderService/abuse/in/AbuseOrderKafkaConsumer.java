package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.kafka.EventEnvelopeReader;
import com.cartmate.cartmate.global.outbox.OutboxTopics;
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

    static final String TOPIC = "#{T(com.cartmate.cartmate.global.outbox.OutboxTopics)"
            + ".of(T(com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent))}";
    static final String GROUP_ID = "order-service-abuse";

    private final EventEnvelopeReader envelopeReader;
    private final AbuseOrderEventConsumer abuseOrderEventConsumer;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void onMessage(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = envelopeReader.envelope(record.value());
        SharedGroupOrderCanceledEvent event = envelopeReader.payload(envelope, SharedGroupOrderCanceledEvent.class);
        log.debug("어뷰징 신호 수신: eventId={}, sharedGroupOrderId={}", envelope.eventId(), event.sharedGroupOrderId());
        abuseOrderEventConsumer.accept(event);
    }
}
