package com.cartmate.cartmate.boundedContext.productService.config;

import com.cartmate.cartmate.global.kafka.InboundEventBinding;
import com.cartmate.cartmate.global.outbox.OutboxTopics;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseUpdatedEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderCreatedEvent;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositFailedEvent;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositedEvent;
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

    @Bean
    public InboundEventBinding orderCreatedBinding() {
        return binding(OrderCreatedEvent.class);
    }

    @Bean
    public InboundEventBinding orderPaidBinding() {
        return binding(OrderPaidEvent.class);
    }

    @Bean
    public InboundEventBinding orderCanceledBinding() {
        return binding(OrderCanceledEvent.class);
    }

    @Bean
    public InboundEventBinding orderSellerItemsCanceledBinding() {
        return binding(OrderSellerItemsCanceledEvent.class);
    }

    @Bean
    public InboundEventBinding deliveryConfirmedBinding() {
        return binding(DeliveryConfirmedEvent.class);
    }

    @Bean
    public InboundEventBinding paybackDepositedBinding() {
        return binding(PaybackDepositedEvent.class);
    }

    @Bean
    public InboundEventBinding paybackDepositFailedBinding() {
        return binding(PaybackDepositFailedEvent.class);
    }

    private static InboundEventBinding binding(Class<?> eventType) {
        return new InboundEventBinding(OutboxTopics.of(eventType), eventType);
    }
}
