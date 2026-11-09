package com.cartmate.cartmate.boundedContext.paymentService.config;

import com.cartmate.cartmate.global.kafka.InboundEventBinding;
import com.cartmate.cartmate.global.outbox.OutboxTopics;
import com.cartmate.cartmate.shared.delivery.event.DeliveryClaimApprovedEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import com.cartmate.cartmate.shared.member.event.MemberSignedUpEvent;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderSellerItemsCanceledEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 결제 서비스가 Kafka로 받는 토픽. 받은 이벤트는 KafkaInboundEventBridge가 inbox_event에 저장하고, 인박스 워커가
 * <b>원래 이벤트 그대로</b> 워커 트랜잭션 안에서 발행한다 → 리스너(PaymentOrderEventListener, PaymentDeliveryEventListener,
 * PayoutEventListener, WalletEventListener)가 받는다.
 *
 * <p>토픽 이름은 OutboxTopics로 이벤트 클래스에서 만든다 (보내는 쪽과 같은 규칙이라 오타로 어긋나지 않는다).
 * 판매자 등록은 보내는 쪽이 Outbox로 바뀌면 여기에 추가한다.
 */
@Configuration
@ConditionalOnProperty(name = "cartmate.kafka.enabled", havingValue = "true")
public class PaymentServiceKafkaConfig {

    @Bean
    public InboundEventBinding orderCanceledBinding() {
        return binding(OrderCanceledEvent.class);
    }

    @Bean
    public InboundEventBinding orderSellerItemsCanceledBinding() {
        return binding(OrderSellerItemsCanceledEvent.class);
    }

    @Bean
    public InboundEventBinding sharedGroupOrderCanceledBinding() {
        return binding(SharedGroupOrderCanceledEvent.class);
    }

    @Bean
    public InboundEventBinding sharedGroupOrderSellerItemsCanceledBinding() {
        return binding(SharedGroupOrderSellerItemsCanceledEvent.class);
    }

    @Bean
    public InboundEventBinding deliveryConfirmedBinding() {
        return binding(DeliveryConfirmedEvent.class);
    }

    @Bean
    public InboundEventBinding deliveryClaimApprovedBinding() {
        return binding(DeliveryClaimApprovedEvent.class);
    }

    @Bean
    public InboundEventBinding memberSignedUpBinding() {
        return binding(MemberSignedUpEvent.class);
    }

    private static InboundEventBinding binding(Class<?> eventType) {
        return new InboundEventBinding(OutboxTopics.of(eventType), eventType);
    }
}
