package com.cartmate.cartmate.boundedContext.payment.out;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

// [임시] order 모듈의 개인 주문이 완성되기 전까지 쓰는 목업.
// 실제 구현이 생기면 이 클래스를 삭제한다 (빈이 두 개면 앱이 안 뜸)
@Component
public class MockPaymentOrderClient implements PaymentOrderClient {

    // 주문ID → (주문자 회원ID, 금액, 결제 가능 여부)
    // 회원ID는 회원 더미 SQL 기준: 3 = buyer@cartmate.com, 4 = runner@cartmate.com
    private static final Map<Long, PaymentOrderInfo> ORDERS = Map.of(
            1001L, new PaymentOrderInfo(1001L, 3L, 45_000L, true),
            1002L, new PaymentOrderInfo(1002L, 3L, 18_000L, true),
            1003L, new PaymentOrderInfo(1003L, 3L, 500_000L, true),  // 잔액 부족 확인용
            1004L, new PaymentOrderInfo(1004L, 4L, 22_000L, true),   // 다른 회원 주문
            1005L, new PaymentOrderInfo(1005L, 3L, 30_000L, false)   // 이미 취소된 주문 등
    );

    @Override
    public Optional<PaymentOrderInfo> findOrder(Long orderId) {
        return Optional.ofNullable(ORDERS.get(orderId));
    }
}