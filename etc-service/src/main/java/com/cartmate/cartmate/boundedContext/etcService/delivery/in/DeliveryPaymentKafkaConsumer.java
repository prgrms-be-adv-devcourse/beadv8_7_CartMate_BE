package com.cartmate.cartmate.boundedContext.etcService.delivery.in;

import com.cartmate.cartmate.boundedContext.etcService.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.global.kafka.InboundEvents;
import com.cartmate.cartmate.shared.payment.event.ClaimRefundCompletedEvent;
import com.cartmate.cartmate.shared.payment.event.ClaimRefundFailedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cartmate.kafka.enabled", havingValue = "true")
public class DeliveryPaymentKafkaConsumer {

    static final String COMPLETED_TOPIC = "#{T(com.cartmate.cartmate.global.outbox.OutboxTopics)"
            + ".of(T(com.cartmate.cartmate.shared.payment.event.ClaimRefundCompletedEvent))}";
    static final String FAILED_TOPIC = "#{T(com.cartmate.cartmate.global.outbox.OutboxTopics)"
            + ".of(T(com.cartmate.cartmate.shared.payment.event.ClaimRefundFailedEvent))}";
    static final String COMPLETED_GROUP_ID = "etc-service-claim-refund-completed";
    static final String FAILED_GROUP_ID = "etc-service-claim-refund-failed";

    private final DeliveryFacade deliveryFacade;
    private final InboundEvents inboundEvents;

    @KafkaListener(topics = COMPLETED_TOPIC, groupId = COMPLETED_GROUP_ID)
    public void onCompleted(ConsumerRecord<String, String> record) {
        inboundEvents.process(record, ClaimRefundCompletedEvent.class,
                event -> deliveryFacade.completeClaimRefund(event.claimId()));
    }

    @KafkaListener(topics = FAILED_TOPIC, groupId = FAILED_GROUP_ID)
    public void onFailed(ConsumerRecord<String, String> record) {
        inboundEvents.process(record, ClaimRefundFailedEvent.class,
                event -> deliveryFacade.failClaimRefund(event.claimId()));
    }
}
