package com.cartmate.cartmate.boundedContext.paymentService.payout.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PayoutStatus {
    PENDING("정산 대기"),
    COMPLETED("정산 완료"),
    CANCELED("정산 취소"),
    FAILED("정산 실패");

    private final String label;
}
