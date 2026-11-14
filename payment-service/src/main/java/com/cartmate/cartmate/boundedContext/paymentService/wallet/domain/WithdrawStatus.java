package com.cartmate.cartmate.boundedContext.paymentService.wallet.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WithdrawStatus {
    PENDING("출금 대기"),
    COMPLETED("출금 완료"),
    CANCELED("출금 취소"),
    FAILED("출금 실패");

    private final String label;
}
