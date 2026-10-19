package com.cartmate.cartmate.boundedContext.payout.app.dto;

import java.time.LocalDate;

// 판매자의 정산 정책과 정산 현황.
public record SettlementInfoResponse(
        int feeRatePercent,             // 수수료율(%)
        int payoutDayOfMonth,           // 매월 지급일
        LocalDate nextPayoutDate,       // 다음 지급일
        long pendingPayoutCount,        // 지급 예정(정산 예정) 건수
        long pendingPayoutAmount,       // 지급 예정 금액(지급액 합계)
        long completedPayoutAmount      // 지금까지 지급 완료된 금액(지급액 합계)
) {
}
