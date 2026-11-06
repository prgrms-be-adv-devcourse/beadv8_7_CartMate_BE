package com.cartmate.cartmate.boundedContext.productService.coupon.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 회원이 쿠폰을 받는 방법
@Getter
@RequiredArgsConstructor
public enum CouponIssueType {
    AUTO("조건을 채우면 자동 발급"),
    DOWNLOAD("회원이 직접 다운로드");

    private final String description;
}
