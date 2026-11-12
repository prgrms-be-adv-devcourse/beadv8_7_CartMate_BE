package com.cartmate.cartmate.shared.cart;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SharedGroupPurchaseStatus {
    OPEN("모집 중"),
    FROZEN("마감 보류"),  // 판매 불가 상품이 있어 수정 대기
    CLOSED("마감"),       // 마감, 공동 주문 생성 대기
    ORDER_CREATED("주문 생성됨"),
    CANCELED("취소");

    private final String label;
}
