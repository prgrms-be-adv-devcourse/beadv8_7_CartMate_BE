package com.cartmate.cartmate.boundedContext.payout.app.dto;

import com.cartmate.cartmate.boundedContext.payout.domain.Payout;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 구매확정되었지만 아직 지급되지 않은 정산 한 건(주문 상품 하나). 상품명, 수량, 주문 시각은 기록되기 전에 만든 정산이면 null이다.
public record PendingPayoutResponse(
        Long payoutId,
        LocalDate settlementDate,   // 지급 예정일
        LocalDateTime orderedAt,    // 주문 시각
        LocalDateTime confirmedAt,  // 구매확정 시각
        String productName,
        Long quantity,
        long salesAmount,           // 판매 금액
        long feeAmount,             // 수수료
        long payoutAmount           // 지급액(판매 금액 - 수수료)
) {
    public static PendingPayoutResponse from(Payout payout) {
        return new PendingPayoutResponse(
                payout.getId(), payout.getScheduledAt().toLocalDate(), payout.getOrderedAt(), payout.getConfirmedAt(),
                payout.getProductName(), payout.getQuantity(),
                payout.getSalesAmount(), payout.getFeeAmount(), payout.getPayoutAmount());
    }
}
