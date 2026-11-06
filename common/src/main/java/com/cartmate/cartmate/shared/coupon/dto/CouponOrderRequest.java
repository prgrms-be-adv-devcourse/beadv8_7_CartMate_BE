package com.cartmate.cartmate.shared.coupon.dto;

public record CouponOrderRequest(
        Long memberId,
        Long memberCouponId,
        String orderType,
        Long orderId,
        CouponOrderTarget target
) {
}
