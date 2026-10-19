package com.cartmate.cartmate.boundedContext.payment.out;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

// [임시] order 모듈의 개인 주문 결제 연동 전까지 쓰는 목업.
// 실제 구현이 생기면 이 클래스를 삭제한다 (빈이 두 개면 앱이 안 뜸)
@Component
public class MockPaymentOrderClient implements PaymentOrderClient {

    // 로컬 DB의 회원 번호 (SELECT id, email FROM member; 로 확인)
    private static final Long BUYER_ID = 4L;  // buyer@cartmate.com
    private static final Long OTHER_ID = 5L;  // 다른 회원 (주문 접근 거부 확인용)

    private static final Map<Long, PaymentOrderInfo> ORDERS = Map.of(
            1001L, new PaymentOrderInfo(1001L, BUYER_ID, 45_000L, true),
            1002L, new PaymentOrderInfo(1002L, BUYER_ID, 18_000L, true),
            1003L, new PaymentOrderInfo(1003L, BUYER_ID, 500_000L, true),  // 잔액 부족 확인용
            1004L, new PaymentOrderInfo(1004L, OTHER_ID, 22_000L, true),   // 다른 회원 주문
            1005L, new PaymentOrderInfo(1005L, BUYER_ID, 30_000L, false)   // 결제 불가 주문
    );

    @Override
    public Optional<PaymentOrderInfo> findOrder(Long orderId) {
        return Optional.ofNullable(ORDERS.get(orderId));
    }
}