package com.cartmate.cartmate.boundedContext.orderService.coupon.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 쿠폰(설정) 상태. 중지하면 새로 발급되지 않고, 이미 받은 쿠폰은 그대로 쓸 수 있다
@Getter
@RequiredArgsConstructor
public enum CouponStatus {
    ACTIVE("발급 중"),
    STOPPED("발급 중지");

    private final String description;
}
