package com.cartmate.cartmate.boundedContext.payout.app.dto;

import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository.SettlementSummary;

import java.time.LocalDate;

// 판매자의 정산 내역 한 줄. 같은 지급일·같은 상태의 정산(주문 상품 단위)을 묶은 합계다.
public record SettlementResponse(
        LocalDate settlementDate,   // 지급 예정일
        PayoutStatus status,        // PENDING(정산 예정), COMPLETED(정산 완료)
        long payoutCount,           // 묶인 정산 건수
        long salesAmount,           // 판매 금액 합계
        long feeAmount,             // 수수료 합계
        long payoutAmount           // 지급액 합계(판매 금액 - 수수료)
) {
    public static SettlementResponse from(SettlementSummary summary) {
        return new SettlementResponse(
                summary.getScheduledAt().toLocalDate(), summary.getStatus(), summary.getPayoutCount(),
                summary.getSalesAmount(), summary.getFeeAmount(), summary.getPayoutAmount());
    }
}
