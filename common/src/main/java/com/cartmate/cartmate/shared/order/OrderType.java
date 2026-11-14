package com.cartmate.cartmate.shared.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderType {
    PERSONAL("일반 주문"),  // 장바구니로 만든 개인 주문
    INDIVIDUAL_GROUP("개인물품 공동구매 주문");

    private final String label;
}
