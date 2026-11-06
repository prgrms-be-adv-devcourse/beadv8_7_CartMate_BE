package com.cartmate.cartmate.boundedContext.orderService.config;

import com.cartmate.cartmate.global.kafka.InboundEventBinding;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import com.cartmate.cartmate.shared.post.event.SalesPostHiddenEvent;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositFailedEvent;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 주문 서버가 Kafka로 받는 다른 서비스 이벤트(MSA-5 1단계). common의 수신 다리가 이 토픽들을 구독해
 * 같은 이벤트를 Spring 이벤트로 다시 발행하므로, 받는 리스너는 서버를 나누기 전 그대로다.
 *
 * <p>보내는 쪽(상품 · 배송 서버)이 같은 봉투({@code EventEnvelope} JSON, 키 aggregateId, payload = 이벤트 JSON)로
 * 이 토픽에 발행해야 도착한다. 아직 보내는 쪽 전환 전이다 — 계약은 담당자에게 전달.
 */
@Configuration
@ConditionalOnProperty(name = "cartmate.kafka.enabled", havingValue = "true")
public class OrderServiceKafkaConfig {

    // 판매글 숨김 → 진행 중 주문 취소(OrderPostEventListener) · 모집 중 공동구매에서 상품 제외(CartPostEventListener)
    public static final String SALES_POST_HIDDEN_TOPIC = "cartmate.post.sales-post-hidden";

    // 구매확정 → 개인물품 공동구매 페이백 판정(IndividualGroupPurchasePaybackListener)
    public static final String DELIVERY_CONFIRMED_TOPIC = "cartmate.delivery.delivery-confirmed";

    // 페이백 예치금 적립 결과 → 지급 행 PAID · 재요청 대기 · FAILED(PaybackResultListener)
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
