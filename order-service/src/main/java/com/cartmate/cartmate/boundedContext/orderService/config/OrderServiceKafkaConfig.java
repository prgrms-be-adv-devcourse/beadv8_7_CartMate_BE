package com.cartmate.cartmate.boundedContext.orderService.config;

import com.cartmate.cartmate.global.outbox.OutboxTopics;
import com.cartmate.cartmate.shared.member.event.MemberSignedUpEvent;
import com.cartmate.cartmate.global.kafka.InboundEventBinding;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import com.cartmate.cartmate.shared.post.event.SalesPostHiddenEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "cartmate.kafka.enabled", havingValue = "true")
public class OrderServiceKafkaConfig {

    public static final String SALES_POST_HIDDEN_TOPIC = "cartmate.post.sales-post-hidden";

    public static final String DELIVERY_CONFIRMED_TOPIC = "cartmate.delivery.delivery-confirmed";

    @Bean
    public InboundEventBinding salesPostHiddenBinding() {
        return new InboundEventBinding(SALES_POST_HIDDEN_TOPIC, SalesPostHiddenEvent.class);
    }

    // 쿠폰: 가입하면 가입 쿠폰 자동 발급 (회원 서비스 → 주문 서비스)
    @Bean
    public InboundEventBinding memberSignedUpBinding() {
        return new InboundEventBinding(OutboxTopics.of(MemberSignedUpEvent.class), MemberSignedUpEvent.class);
    }

    @Bean
    public InboundEventBinding deliveryConfirmedBinding() {
        return new InboundEventBinding(DELIVERY_CONFIRMED_TOPIC, DeliveryConfirmedEvent.class);
    }
}
