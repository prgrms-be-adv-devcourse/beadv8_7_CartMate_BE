package com.cartmate.cartmate.boundedContext.memberService.member.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SellerStatus {
    ACTIVE("정상"),
    SUSPENDED("이용 정지");

    private final String label;
}