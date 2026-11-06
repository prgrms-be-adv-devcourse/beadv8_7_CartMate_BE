package com.cartmate.cartmate.boundedContext.paymentService.payout.in;

import com.cartmate.cartmate.boundedContext.paymentService.payout.app.PayoutFacade;
import com.cartmate.cartmate.global.kafka.InboundEvents;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cartmate.kafka.enabled", havingValue = "true")
public class PayoutDeliveryKafkaConsumer {

    static final String TOPIC = "#{T(com.cartmate.cartmate.global.outbox.OutboxTopics)"
            + ".of(T(com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent))}";
    static final String GROUP_ID = "payment-service-payout";

    private final PayoutFacade payoutFacade;
    private final InboundEvents inboundEvents;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void onMessage(ConsumerRecord<String, String> record) {
        inboundEvents.process(record, DeliveryConfirmedEvent.class, payoutFacade::createPayouts);
    }
}
