package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AbuseLevel {
    NONE("정상"),
    CAUTION("주의"),
    SUSPICIOUS("의심"),
    RESTRICTED("제한");

    private final String label;
}
