package com.cartmate.cartmate.boundedContext.paymentService.wallet.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChargeStatus {
    PENDING("충전 대기"),
    COMPLETED("충전 완료"),
    CANCELED("충전 취소"),
    FAILED("충전 실패");

    private final String label;
}
