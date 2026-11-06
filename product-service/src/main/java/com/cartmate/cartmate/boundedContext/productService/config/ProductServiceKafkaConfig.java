package com.cartmate.cartmate.boundedContext.productService.config;

import com.cartmate.cartmate.global.kafka.InboundEventBinding;
import com.cartmate.cartmate.global.outbox.OutboxTopics;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseUpdatedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "cartmate.kafka.enabled", havingValue = "true")
public class ProductServiceKafkaConfig {

    @Bean
    public InboundEventBinding individualGroupPurchaseOpenedBinding() {
        return binding(IndividualGroupPurchaseOpenedEvent.class);
    }

    @Bean
    public InboundEventBinding individualGroupPurchaseUpdatedBinding() {
        return binding(IndividualGroupPurchaseUpdatedEvent.class);
    }

    @Bean
    public InboundEventBinding individualGroupPurchaseClosedBinding() {
        return binding(IndividualGroupPurchaseClosedEvent.class);
    }

    private static InboundEventBinding binding(Class<?> eventType) {
        return new InboundEventBinding(OutboxTopics.of(eventType), eventType);
    }
}
