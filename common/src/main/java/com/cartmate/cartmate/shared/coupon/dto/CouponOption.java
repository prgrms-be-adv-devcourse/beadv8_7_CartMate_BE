package com.cartmate.cartmate.shared.coupon.dto;

import java.time.LocalDateTime;

/**
 * 결제 화면의 쿠폰 한 줄. 쓸 수 없으면 usable=false와 이유(화면에서 회색 + 문구).
 *
 * @param expectedDiscount 이 주문에 쓰면 할인될 금액 (쓸 수 없으면 0)
 */
public record CouponOption(
        Long memberCouponId,
        Long couponId,
        String name,
        String discountType,
        long discountValue,
        Long maxDiscountAmount,
        long minOrderAmount,
        String scope,
        Long sellerId,
        LocalDateTime expiresAt,
        boolean usable,
        String unusableReason,
        long expectedDiscount
) {
}
