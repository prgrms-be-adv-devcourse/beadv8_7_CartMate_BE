package com.cartmate.cartmate.boundedContext.orderService.order.dto.personal;

import com.cartmate.cartmate.shared.cart.PaybackFunder;
import com.cartmate.cartmate.shared.order.GroupPurchaseType;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.PaybackPayout;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.PaybackPayoutStatus;

import java.time.LocalDateTime;

public record PaybackPayoutResponse(
        Long paybackId,
        GroupPurchaseType groupPurchaseType,
        Long groupPurchaseId,
        Long memberId,
        Long productId,
        PaybackFunder funder,
        Long sellerId,
        int quantity,
        long amountPerUnit,
        long amount,
        PaybackPayoutStatus status,
        int attempt,
        String failureCode,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {

    public static PaybackPayoutResponse from(PaybackPayout payout) {
        return new PaybackPayoutResponse(
                payout.getId(),
                payout.getGroupPurchaseType(),
                payout.getGroupPurchaseId(),
                payout.getMemberId(),
                payout.getProductId(),
                payout.getFunder(),
                payout.getSellerId(),
                payout.getQuantity(),
                payout.getAmountPerUnit(),
                payout.getAmount(),
                payout.getStatus(),
                payout.getAttempt(),
                payout.getFailureCode(),
                payout.getRequestedAt(),
                payout.getCompletedAt());
    }
}
