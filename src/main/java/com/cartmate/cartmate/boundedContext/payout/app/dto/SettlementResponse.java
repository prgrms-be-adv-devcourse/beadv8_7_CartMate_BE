package com.cartmate.cartmate.boundedContext.payout.app.dto;

import com.cartmate.cartmate.boundedContext.payout.domain.PayoutPolicy;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.payout.domain.SettlementKey;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository.SettlementSummary;

import java.time.LocalDate;

// 판매자의 정산 내역 한 줄. 같은 지급일·같은 상태의 정산(주문 상품 단위)을 묶은 합계다.
public record SettlementResponse(
        String settlementId,        // 지급 예정일 + 상태 (예: 20261115-COMPLETED). 정산 상세 조회에 쓴다.
        LocalDate settlementDate,   // 지급 예정일
        PayoutStatus status,        // PENDING(정산 예정), COMPLETED(정산 완료)
        LocalDate periodStart,      // 정산 대상 기간 시작일(구매확정한 달의 1일)
        LocalDate periodEnd,        // 정산 대상 기간 종료일(구매확정한 달의 말일)
        long payoutCount,           // 묶인 정산 건수
        long salesAmount,           // 판매 금액 합계
        long feeAmount,             // 수수료 합계
        long payoutAmount           // 지급액 합계(판매 금액 - 수수료)
) {
    public static SettlementResponse from(SettlementSummary summary) {
        LocalDate settlementDate = summary.getScheduledAt().toLocalDate();

        return new SettlementResponse(
                new SettlementKey(settlementDate, summary.getStatus()).id(), settlementDate, summary.getStatus(),
                PayoutPolicy.confirmPeriodStart(settlementDate), PayoutPolicy.confirmPeriodEnd(settlementDate),
                summary.getPayoutCount(), summary.getSalesAmount(), summary.getFeeAmount(), summary.getPayoutAmount());
    }
}
