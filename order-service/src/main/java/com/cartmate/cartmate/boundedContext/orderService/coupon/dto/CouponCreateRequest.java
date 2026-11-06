package com.cartmate.cartmate.boundedContext.orderService.coupon.dto;

import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.CouponAutoTrigger;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.CouponDiscountType;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.CouponIssueType;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.CouponScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

// 관리자 쿠폰 만들기. 항목 간 규칙(정률 100% 이하, 사용 기간은 둘 중 하나 등)은 Coupon.create가 확인한다
public record CouponCreateRequest(
        @NotBlank(message = "쿠폰 이름을 입력해주세요.") @Size(max = 100) String name,
        @NotNull CouponDiscountType discountType,
        @Positive(message = "할인 값은 0보다 커야 합니다.") long discountValue,
        @Positive Long maxDiscountAmount,
        @PositiveOrZero Long minOrderAmount,
        @NotNull CouponScope scope,
        Long sellerId,
        @NotNull CouponIssueType issueType,
        CouponAutoTrigger autoTrigger,
        LocalDateTime issueStartAt,
        LocalDateTime issueEndAt,
        LocalDateTime usableUntil,
        @Positive Integer validDays,
        @Positive Integer totalQuantity
) {
}
