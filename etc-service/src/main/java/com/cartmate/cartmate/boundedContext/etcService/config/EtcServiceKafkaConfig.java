package com.cartmate.cartmate.boundedContext.etcService.config;

import com.cartmate.cartmate.global.kafka.InboundEventBinding;
import com.cartmate.cartmate.global.outbox.OutboxTopics;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderConfirmedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "cartmate.kafka.enabled", havingValue = "true")
public class EtcServiceKafkaConfig {

    @Bean
    public InboundEventBinding orderPaidBinding() {
        return binding(OrderPaidEvent.class);
    }

    @Bean
    public InboundEventBinding sharedGroupOrderConfirmedBinding() {
        return binding(SharedGroupOrderConfirmedEvent.class);
    }

    private static InboundEventBinding binding(Class<?> eventType) {
        return new InboundEventBinding(OutboxTopics.of(eventType), eventType);
    }
}
