package com.cartmate.cartmate.shared.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 개인 주문인지 개인물품 공동구매 주문인지. 이벤트 · 내부 API로 다른 서비스에 나간다
// 서비스 사이 계약 값(ARCH-8): 이름을 바꾸거나 지우면 받는 서비스가 깨진다. 값은 추가만 하고, 설명 없이는 추가할 수 없다.
@Getter
@RequiredArgsConstructor
public enum OrderType {
    PERSONAL("장바구니로 만든 개인 주문"),
    INDIVIDUAL_GROUP("개인물품 공동구매 주문");

    private final String description;
}
