package com.cartmate.cartmate.boundedContext.productService.coupon.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 할인 방식
@Getter
@RequiredArgsConstructor
public enum CouponDiscountType {
    FIXED("정액"),
    RATE("정률");

    private final String label;
}
