package com.cartmate.cartmate.boundedContext.orderService.coupon.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 자동 발급 조건 (1단계는 가입만)
@Getter
@RequiredArgsConstructor
public enum CouponAutoTrigger {
    SIGNUP("회원 가입");

    private final String description;
}