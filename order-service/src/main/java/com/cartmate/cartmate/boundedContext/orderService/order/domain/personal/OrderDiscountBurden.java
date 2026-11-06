package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 쿠폰 할인을 누가 부담하나 (정산에 반영): 전체 쿠폰 = 플랫폼, 판매자 쿠폰 = 그 판매자
@Getter
@RequiredArgsConstructor
public enum OrderDiscountBurden {
    PLATFORM("플랫폼 부담"),
    SELLER("판매자 부담");

    private final String description;
}
