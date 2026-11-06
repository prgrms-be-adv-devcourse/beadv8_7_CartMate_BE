package com.cartmate.cartmate.boundedContext.orderService.config;

import com.cartmate.cartmate.global.kafka.InboundEventBinding;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import com.cartmate.cartmate.shared.post.event.SalesPostHiddenEvent;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositFailedEvent;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "cartmate.kafka.enabled", havingValue = "true")
public class OrderServiceKafkaConfig {

    public static final String SALES_POST_HIDDEN_TOPIC = "cartmate.post.sales-post-hidden";

    public static final String DELIVERY_CONFIRMED_TOPIC = "cartmate.delivery.delivery-confirmed";

    public static final String PAYBACK_DEPOSITED_TOPIC = "cartmate.wallet.payback-deposited";
    public static final String PAYBACK_DEPOSIT_FAILED_TOPIC = "cartmate.wallet.payback-deposit-failed";

    @Bean
    public InboundEventBinding salesPostHiddenBinding() {
        return new InboundEventBinding(SALES_POST_HIDDEN_TOPIC, SalesPostHiddenEvent.class);
    }

    @Bean
    public InboundEventBinding deliveryConfirmedBinding() {
        return new InboundEventBinding(DELIVERY_CONFIRMED_TOPIC, DeliveryConfirmedEvent.class);
    }

    @Bean
    public InboundEventBinding paybackDepositedBinding() {
        return new InboundEventBinding(PAYBACK_DEPOSITED_TOPIC, PaybackDepositedEvent.class);
    }

    @Bean
    public InboundEventBinding paybackDepositFailedBinding() {
        return new InboundEventBinding(PAYBACK_DEPOSIT_FAILED_TOPIC, PaybackDepositFailedEvent.class);
    }
}
