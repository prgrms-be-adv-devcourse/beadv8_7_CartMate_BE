package com.cartmate.cartmate.shared.cart;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SharedGroupPurchaseCancelReason {
    CREATOR_CANCELED("개설자 취소"),
    NO_ITEMS_AT_DEADLINE("마감 시 담긴 상품 없음"),
    NO_ITEMS_AFTER_FROZEN("마감 보류 후 남은 상품 없음"),
    SALES_STOPPED("판매 중지");

    private final String label;
}
