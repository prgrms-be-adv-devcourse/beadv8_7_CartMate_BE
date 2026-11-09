package com.cartmate.cartmate.boundedContext.orderService.config;

import com.cartmate.cartmate.global.kafka.InboundEventBinding;
import com.cartmate.cartmate.global.outbox.OutboxTopics;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliveryOrderCancelRejectedEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliveryOrderCanceledEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliverySalesStopCancelResultEvent;
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

    @Bean
    public InboundEventBinding deliveryConfirmedBinding() {
        return new InboundEventBinding(DELIVERY_CONFIRMED_TOPIC, DeliveryConfirmedEvent.class);
    }

    @Bean
    public InboundEventBinding deliveryOrderCanceledBinding() {
        return new InboundEventBinding(OutboxTopics.of(DeliveryOrderCanceledEvent.class), DeliveryOrderCanceledEvent.class);
    }

    @Bean
    public InboundEventBinding deliveryOrderCancelRejectedBinding() {
        return new InboundEventBinding(
                OutboxTopics.of(DeliveryOrderCancelRejectedEvent.class), DeliveryOrderCancelRejectedEvent.class);
    }

    @Bean
    public InboundEventBinding deliverySalesStopCancelResultBinding() {
        return new InboundEventBinding(
                OutboxTopics.of(DeliverySalesStopCancelResultEvent.class), DeliverySalesStopCancelResultEvent.class);
    }
}
