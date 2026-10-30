package com.cartmate.cartmate.boundedContext.delivery.app.dto;

import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimReason;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaim;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaimItem;

import java.time.LocalDateTime;
import java.util.List;

// 판매자 교환·환불 관리 목록의 한 행. 주문번호, 상품, 유형, 사유, 신청일, 상태를 담는다.
public record SellerClaimResponse(
        Long claimId,
        Long deliveryId,
        Long orderId,
        String orderNumber,
        ClaimType claimType,
        ClaimReason reason,
        String detail,
        String rejectReason,
        ClaimStatus status,
        LocalDateTime createdAt,
        List<DeliveryClaimResponse.Item> items
) {
    public static SellerClaimResponse from(DeliveryClaim claim, List<DeliveryClaimItem> claimItems) {
        return new SellerClaimResponse(
                claim.getId(),
                claim.getDelivery().getId(),
                claim.getDelivery().getOrderId(),
                claim.getDelivery().getOrderNumber(),
                claim.getClaimType(),
                claim.getReason(),
                claim.getDetail(),
                claim.getRejectReason(),
                claim.getStatus(),
                claim.getCreatedAt(),
                claimItems.stream().map(DeliveryClaimResponse.Item::from).toList()
        );
    }
}
