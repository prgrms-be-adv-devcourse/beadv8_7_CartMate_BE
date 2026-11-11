package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AbuseIdentityType {
    PHONE("전화번호"),
    ADDRESS("주소");

    private final String label;
}
