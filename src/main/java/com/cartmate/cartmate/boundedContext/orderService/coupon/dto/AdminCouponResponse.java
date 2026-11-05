package com.cartmate.cartmate.boundedContext.orderService.coupon.dto;

import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.Coupon;

import java.time.LocalDateTime;

public record AdminCouponResponse(
        Long id,
        String name,
        String discountType,
        long discountValue,
        Long maxDiscountAmount,
        long minOrderAmount,
        String scope,
        Long sellerId,
        String issueType,
        String autoTrigger,
        LocalDateTime issueStartAt,
        LocalDateTime issueEndAt,
        LocalDateTime usableUntil,
        Integer validDays,
        Integer totalQuantity,
        int issuedCount,
        long usedCount,
        String status,
        LocalDateTime createdAt
) {
    public static AdminCouponResponse of(Coupon c, long usedCount) {
        return new AdminCouponResponse(c.getId(), c.getName(), c.getDiscountType().name(), c.getDiscountValue(),
                c.getMaxDiscountAmount(), c.getMinOrderAmount(), c.getScope().name(), c.getSellerId(),
                c.getIssueType().name(), c.getAutoTrigger() == null ? null : c.getAutoTrigger().name(),
                c.getIssueStartAt(), c.getIssueEndAt(), c.getUsableUntil(), c.getValidDays(), c.getTotalQuantity(),
                c.getIssuedCount(), usedCount, c.getStatus().name(), c.getCreateDate());
    }
}