package com.cartmate.cartmate.boundedContext.orderService.config;

import com.cartmate.cartmate.global.kafka.RelaySubscription;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
@ConditionalOnProperty(name = "cartmate.kafka.enabled", havingValue = "true")
public class OrderServiceRelayConfig {

    public static final String RELAY_GROUP_ID = "order-service-relay";

    @Bean
    public RelaySubscription orderServiceRelaySubscription() {
        return new RelaySubscription(RELAY_GROUP_ID, Set.of("cart", "order"));
    }
}
