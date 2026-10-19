package com.cartmate.cartmate.boundedContext.payment.out;

import java.util.Optional;

// order 모듈이 구현할 인터페이스. 개인 주문이 생기면 order 담당이 실제 구현을 만든다
public interface PaymentOrderClient {
    Optional<PaymentOrderInfo> findOrder(Long orderId);
}