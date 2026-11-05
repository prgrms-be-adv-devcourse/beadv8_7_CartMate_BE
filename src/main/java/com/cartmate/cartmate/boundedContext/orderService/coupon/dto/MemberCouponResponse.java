package com.cartmate.cartmate.boundedContext.orderService.coupon.dto;

import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.Coupon;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.MemberCoupon;

import java.time.LocalDateTime;

// 쿠폰함 한 줄. status: AVAILABLE(사용 가능) / RESERVED(사용 예정) / USED(사용 완료) / EXPIRED(기간 만료)
public record MemberCouponResponse(
        Long memberCouponId,
        Long couponId,
        String name,
        String discountType,
        long discountValue,
        Long maxDiscountAmount,
        long minOrderAmount,
        String scope,
        Long sellerId,
        String status,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt,
        LocalDateTime usedAt
) {
    public static MemberCouponResponse of(MemberCoupon mc, Coupon c, LocalDateTime now) {
        return new MemberCouponResponse(mc.getId(), c.getId(), c.getName(), c.getDiscountType().name(),
                c.getDiscountValue(), c.getMaxDiscountAmount(), c.getMinOrderAmount(), c.getScope().name(),
                c.getSellerId(), mc.displayStatus(now), mc.getIssuedAt(), mc.getExpiresAt(), mc.getUsedAt());
    }
}