package com.cartmate.cartmate.boundedContext.memberService.member.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuspensionReason {
    FRAUD("사기"),
    ABUSE("어뷰징"),
    OTHER("기타");

    private final String label;
}