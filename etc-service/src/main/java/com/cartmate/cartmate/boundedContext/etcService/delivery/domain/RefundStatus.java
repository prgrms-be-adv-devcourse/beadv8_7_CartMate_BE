package com.cartmate.cartmate.boundedContext.etcService.delivery.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RefundStatus {
    NONE("환불 없음"),
    PENDING("환불 대기"),
    COMPLETED("환불 완료"),
    FAILED("환불 실패");

    private final String label;
}
