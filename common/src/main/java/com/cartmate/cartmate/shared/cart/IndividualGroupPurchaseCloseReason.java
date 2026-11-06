package com.cartmate.cartmate.shared.cart;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 개인물품 공동구매가 닫힌 이유
// 서비스 사이 계약 값(ARCH-8): 이름을 바꾸거나 지우면 받는 서비스가 깨진다. 값은 추가만 하고, 설명 없이는 추가할 수 없다.
@Getter
@RequiredArgsConstructor
public enum IndividualGroupPurchaseCloseReason {
    CREATOR("개설자가 마감"),
    ADMIN("다른 어드민이 마감(어드민 공동구매만)"),
    DEADLINE("마감 시각이 지나 시스템이 마감(어드민 공동구매만)"),
    SALES_STOPPED("판매 중지로 상품이 모두 빠짐");

    private final String description;
}
