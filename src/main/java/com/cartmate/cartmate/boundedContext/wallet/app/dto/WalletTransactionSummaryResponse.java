package com.cartmate.cartmate.boundedContext.wallet.app.dto;

public record WalletTransactionSummaryResponse(
        Long totalCharged,  // 전체 기간 충전 합계
        Long totalPaid      // 전체 기간 결제 합계에서 환불을 뺀 순 결제액
) {
}
