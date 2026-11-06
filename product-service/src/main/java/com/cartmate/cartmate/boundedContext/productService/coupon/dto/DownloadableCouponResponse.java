package com.cartmate.cartmate.boundedContext.productService.coupon.dto;

import com.cartmate.cartmate.boundedContext.productService.coupon.domain.Coupon;

import java.time.LocalDateTime;

// 받을 수 있는 쿠폰 (이벤트·판매자 화면의 "쿠폰 받기")
public record DownloadableCouponResponse(
        Long couponId,
        String name,
        String discountType,
        long discountValue,
        Long maxDiscountAmount,
        long minOrderAmount,
        String scope,
        Long sellerId,
        LocalDateTime issueEndAt,
        LocalDateTime usableUntil,
        Integer validDays,
        Integer remaining,      // 남은 수량 (제한 없으면 null)
        boolean downloaded      // 이미 받았는지
) {
    public static DownloadableCouponResponse of(Coupon c, boolean downloaded) {
        Integer remaining = c.getTotalQuantity() == null ? null : Math.max(0, c.getTotalQuantity() - c.getIssuedCount());
        return new DownloadableCouponResponse(c.getId(), c.getName(), c.getDiscountType().name(), c.getDiscountValue(),
                c.getMaxDiscountAmount(), c.getMinOrderAmount(), c.getScope().name(), c.getSellerId(),
                c.getIssueEndAt(), c.getUsableUntil(), c.getValidDays(), remaining, downloaded);
    }
}
