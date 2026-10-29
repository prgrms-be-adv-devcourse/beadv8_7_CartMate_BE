package com.cartmate.cartmate.shared.delivery.event;

import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;

// 판매자가 교환·환불 요청을 승인할 때 발행. payment가 받아서 환불한다.
public record DeliveryClaimApprovedEvent(
        Long claimId,
        Long deliveryId,
        Long orderId,
        PurchaseType purchaseType, // 개인 주문과 공동물품 주문은 주문 ID가 겹칠 수 있어 구분한다.
        Long buyerMemberId,
        Long sellerId,
        String claimType,          // EXCHANGE, REFUND
        Long refundAmount          // 승인된 상품의 단가 × 요청 수량 합계. 교환이면 0
) {
}