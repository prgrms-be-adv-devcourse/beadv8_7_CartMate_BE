package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderDiscountBurden {
    PLATFORM("플랫폼 부담"),
    SELLER("판매자 부담");

    private final String description;
}
