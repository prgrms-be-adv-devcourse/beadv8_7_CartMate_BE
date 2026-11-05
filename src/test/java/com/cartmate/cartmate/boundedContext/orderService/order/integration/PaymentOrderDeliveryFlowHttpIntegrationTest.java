package com.cartmate.cartmate.boundedContext.orderService.order.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 결제 흐름 통합 테스트를 http 모드로 한 번 더 돌린다.
 *
 * <p>주문이 결제를 같은 앱의 Facade가 아니라 결제 내부 API(HTTP)로 부른다.
 * 그래서 차감(결제 요청 트랜잭션)과 주문 반영(주문 트랜잭션)이 서로 다른 트랜잭션이 된다 — 결제 서버를 나눈 뒤와 같은 상황이다.
 * 상위 클래스의 시나리오(결제 → PAID → 차감 → 배송 → 장바구니 정리, 두 번 결제해도 한 번, 잔액 부족 시 그대로)가
 * 이 상황에서도 그대로 통과해야 한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"cartmate.scheduling.enabled=false", "cartmate.payment.client=http"})
@ActiveProfiles("test")
class PaymentOrderDeliveryFlowHttpIntegrationTest extends PaymentOrderDeliveryFlowIntegrationTest {
}