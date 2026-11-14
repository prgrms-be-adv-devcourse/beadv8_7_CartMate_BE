package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AbuseSignalStatus {
    RECEIVED("접수"),
    JUDGED("판정 완료"),
    FAILED("판정 실패");

    private final String label;
}
