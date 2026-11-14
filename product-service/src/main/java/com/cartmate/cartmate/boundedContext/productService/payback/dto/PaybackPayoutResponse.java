package com.cartmate.cartmate.boundedContext.productService.payback.dto;

import com.cartmate.cartmate.shared.product.PaybackFunder;
import com.cartmate.cartmate.shared.cart.GroupPurchaseType;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackPayout;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackPayoutStatus;

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
