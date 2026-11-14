package com.cartmate.cartmate.shared.delivery.event;

import java.time.LocalDateTime;

// 판매자가 수락한 교환·환불을 처리 완료할 때 발행. trust가 받아서 판매자 귀책 이력을 남긴다.
public record DeliveryClaimCompletedEvent(
        Long claimId,
        Long deliveryId,
        Long sellerId,
        String claimType,          // EXCHANGE, REFUND
        String claimReason,        // 클레임 사유(ClaimReason 이름). 판매자 귀책 여부를 가릴 때 쓴다.
        LocalDateTime completedAt  // 처리 완료 시각
) {
}
