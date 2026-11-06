package com.cartmate.cartmate.shared.coupon.dto;

/**
 * 주문 하나에 쿠폰 한 장을 쓸 때의 할인 결과.
 *
 * @param platformBurden 전체 쿠폰이면 true(플랫폼 부담), 판매자 쿠폰이면 false(판매자 부담)
 * @param sellerId       판매자 쿠폰이면 그 판매자, 전체 쿠폰이면 null
 */
public record CouponQuote(
        Long memberCouponId,
        Long couponId,
        String couponName,
        long discountAmount,
        boolean platformBurden,
        Long sellerId
) {
}
