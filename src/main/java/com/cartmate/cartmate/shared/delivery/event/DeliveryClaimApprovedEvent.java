package com.cartmate.cartmate.shared.delivery.event;

import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;

import java.time.LocalDateTime;

// 판매자가 교환·환불 요청을 승인할 때 발행. payment가 받아서 환불하고, trust가 받아서 판매자 귀책 이력을 남긴다.
public record DeliveryClaimApprovedEvent(
        Long claimId,
        Long deliveryId,
        Long orderId,
        PurchaseType purchaseType, // 개인 주문과 공동물품 주문은 주문 ID가 겹칠 수 있어 구분한다.
        Long buyerMemberId,
        Long sellerId,
        String claimType,          // EXCHANGE, REFUND
        String claimReason,        // 클레임 사유(ClaimReason 이름). 판매자 귀책 여부를 가릴 때 쓴다.
        Long refundAmount,         // 승인된 상품의 단가 × 요청 수량 합계. 교환이면 0
        LocalDateTime approvedAt   // 판매자가 승인한 시각
) {
}