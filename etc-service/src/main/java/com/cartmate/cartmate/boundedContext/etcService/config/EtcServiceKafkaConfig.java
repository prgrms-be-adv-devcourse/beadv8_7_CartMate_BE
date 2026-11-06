package com.cartmate.cartmate.boundedContext.etcService.config;

import com.cartmate.cartmate.global.kafka.InboundEventBinding;
import com.cartmate.cartmate.global.outbox.OutboxTopics;
import com.cartmate.cartmate.shared.payment.event.ClaimRefundCompletedEvent;
import com.cartmate.cartmate.shared.payment.event.ClaimRefundFailedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "cartmate.kafka.enabled", havingValue = "true")
public class EtcServiceKafkaConfig {

    @Bean
    public InboundEventBinding claimRefundCompletedBinding() {
        return new InboundEventBinding(OutboxTopics.of(ClaimRefundCompletedEvent.class), ClaimRefundCompletedEvent.class);
    }

    @Bean
    public InboundEventBinding claimRefundFailedBinding() {
        return new InboundEventBinding(OutboxTopics.of(ClaimRefundFailedEvent.class), ClaimRefundFailedEvent.class);
    }
}
