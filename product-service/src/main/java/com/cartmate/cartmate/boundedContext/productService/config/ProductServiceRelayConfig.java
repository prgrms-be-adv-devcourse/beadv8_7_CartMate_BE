package com.cartmate.cartmate.boundedContext.productService.config;

import com.cartmate.cartmate.global.kafka.RelaySubscription;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * product-service가 스스로 보내고 스스로 받는 이벤트(post ↔ product)를 Kafka로 다시 받는다.
 *
 * <p>Kafka를 켜면 outbox 이벤트가 같은 앱 안과 Kafka 양쪽으로 나간다. 받는 쪽은
 * {@code @relayedEventRouting.accepts(#message)} 조건으로 Kafka에서 온 것만 처리한다(order-service와 같은 방식).
 * 그룹을 따로 둬서 다른 서비스 이벤트를 받는 product-service 그룹과 오프셋이 섞이지 않게 한다.
 */
@Configuration
@ConditionalOnProperty(name = "cartmate.kafka.enabled", havingValue = "true")
public class ProductServiceRelayConfig {

    public static final String RELAY_GROUP_ID = "product-service-relay";

    @Bean
    public RelaySubscription productServiceRelaySubscription() {
        return new RelaySubscription(RELAY_GROUP_ID, Set.of("post", "product"));
    }
}
