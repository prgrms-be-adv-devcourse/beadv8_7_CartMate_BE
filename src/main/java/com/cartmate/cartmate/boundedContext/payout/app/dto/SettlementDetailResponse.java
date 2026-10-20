package com.cartmate.cartmate.boundedContext.payout.app.dto;

import com.cartmate.cartmate.boundedContext.payout.domain.Payout;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutPolicy;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.payout.domain.SettlementKey;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 판매자의 정산 상세. 정산 내역 한 줄(같은 지급일·같은 상태)의 합계와, 그 줄에 묶인 판매 건 목록이다.
public record SettlementDetailResponse(
        String settlementId,        // 지급 예정일 + 상태 (예: 20261115-COMPLETED)
        LocalDate settlementDate,   // 지급 예정일
        PayoutStatus status,        // PENDING(정산 예정), COMPLETED(정산 완료)
        LocalDate periodStart,      // 정산 대상 기간 시작일(구매확정한 달의 1일)
        LocalDate periodEnd,        // 정산 대상 기간 종료일(구매확정한 달의 말일)
        long payoutCount,           // 묶인 정산 건수
        long totalQuantity,         // 판매 수량 합계. 수량이 기록되지 않은 옛 정산은 더하지 않는다.
        long salesAmount,           // 판매 금액 합계
        long feeAmount,             // 수수료 합계
        long payoutAmount,          // 지급액 합계(판매 금액 - 수수료)
        List<Item> items            // 묶인 판매 건. 구매확정이 최근인 건이 먼저
) {
    // 판매 건 하나(주문 상품 하나). 상품명, 수량, 주문 시각은 기록되기 전에 만든 정산이면 null이다.
    public record Item(
            Long payoutId,
            LocalDateTime orderedAt,    // 주문 시각
            LocalDateTime confirmedAt,  // 구매확정 시각
            String productName,
            Long quantity,
            long salesAmount,           // 판매 금액(결제 금액)
            long feeAmount,
            long payoutAmount           // 지급액(수수료 제외)
    ) {
        public static Item from(Payout payout) {
            return new Item(
                    payout.getId(), payout.getOrderedAt(), payout.getConfirmedAt(), payout.getProductName(),
                    payout.getQuantity(), payout.getSalesAmount(), payout.getFeeAmount(), payout.getPayoutAmount());
        }
    }

    public static SettlementDetailResponse of(SettlementKey key, List<Payout> payouts) {
        return new SettlementDetailResponse(
                key.id(), key.settlementDate(), key.status(),
                PayoutPolicy.confirmPeriodStart(key.settlementDate()), PayoutPolicy.confirmPeriodEnd(key.settlementDate()),
                payouts.size(),
                payouts.stream().map(Payout::getQuantity).filter(q -> q != null).mapToLong(Long::longValue).sum(),
                payouts.stream().mapToLong(Payout::getSalesAmount).sum(),
                payouts.stream().mapToLong(Payout::getFeeAmount).sum(),
                payouts.stream().mapToLong(Payout::getPayoutAmount).sum(),
                payouts.stream().map(Item::from).toList());
    }
}
