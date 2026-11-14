package com.cartmate.cartmate.boundedContext.etcService.delivery.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClaimType {
    EXCHANGE("교환"),
    REFUND("반품");  // 반품(환불)

    private final String label;
}
