package com.cartmate.cartmate.boundedContext.paymentService.wallet.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HoldingStatus {
    RESERVED("홀딩 중"),
    SETTLED("정산 완료"),
    CANCELED("홀딩 취소");

    private final String label;
}
