package com.cartmate.cartmate.boundedContext.orderService.order.dto.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.PaybackPayout;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.PaybackPayoutStatus;

import java.time.LocalDateTime;

public record PaybackPayoutResponse(
        Long paybackId,
        Long individualGroupPurchaseId,
        Long memberId,
        long amount,
        PaybackPayoutStatus status,
        int attempt,
        String failureCode,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {

    public static PaybackPayoutResponse from(PaybackPayout payout) {
        return new PaybackPayoutResponse(payout.getId(), payout.getIndividualGroupPurchaseId(), payout.getMemberId(),
                payout.getAmount(), payout.getStatus(), payout.getAttempt(), payout.getFailureCode(),
                payout.getRequestedAt(), payout.getCompletedAt());
    }
}
