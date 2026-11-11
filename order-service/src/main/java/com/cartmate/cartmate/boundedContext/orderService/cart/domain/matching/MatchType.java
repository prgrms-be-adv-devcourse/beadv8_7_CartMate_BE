package com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MatchType {
    EXACT("정확 일치"),
    VECTOR("유사 상품");

    private final String label;
}
