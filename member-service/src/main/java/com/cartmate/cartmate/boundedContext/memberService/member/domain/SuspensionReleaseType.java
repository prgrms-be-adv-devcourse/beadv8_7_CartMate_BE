package com.cartmate.cartmate.boundedContext.memberService.member.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuspensionReleaseType {
    EXPIRED("기간 만료"),
    MANUAL("수동 해제");

    private final String label;
}