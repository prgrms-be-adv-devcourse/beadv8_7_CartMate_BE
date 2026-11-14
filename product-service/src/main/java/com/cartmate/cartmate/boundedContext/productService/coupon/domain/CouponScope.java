package com.cartmate.cartmate.boundedContext.productService.coupon.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 쿠폰을 쓸 수 있는 상품 범위 (할인 부담도 이걸로 정해진다: 전체=플랫폼, 판매자=판매자)
@Getter
@RequiredArgsConstructor
public enum CouponScope {
    ALL("전체 상품"),
    SELLER("판매자 상품");  // 특정 판매자 상품

    private final String label;
}
