package com.cartmate.cartmate.boundedContext.memberService.member.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuspensionDuration {
    DAYS_7("7일", 7),
    DAYS_30("30일", 30),
    PERMANENT("영구", null);

    private final String label;
    private final Integer days;
}