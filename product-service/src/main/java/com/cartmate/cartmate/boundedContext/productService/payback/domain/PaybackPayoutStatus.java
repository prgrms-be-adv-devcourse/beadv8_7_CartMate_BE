package com.cartmate.cartmate.boundedContext.productService.payback.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaybackPayoutStatus {
    REQUESTED("지급 요청"),
    RETRY_WAITING("재시도 대기"),
    PAID("지급 완료"),
    FAILED("지급 실패");

    private final String label;
}
