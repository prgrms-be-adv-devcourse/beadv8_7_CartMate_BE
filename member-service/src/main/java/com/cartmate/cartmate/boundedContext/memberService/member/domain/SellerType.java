package com.cartmate.cartmate.boundedContext.memberService.member.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SellerType {
    INDIVIDUAL("개인"),
    BUSINESS("사업자");

    private final String label;
}