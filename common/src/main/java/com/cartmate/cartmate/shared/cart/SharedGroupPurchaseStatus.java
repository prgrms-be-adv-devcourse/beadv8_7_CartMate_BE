package com.cartmate.cartmate.shared.cart;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 공용물품 공동구매 상태. OPEN → (FROZEN) → CLOSED → ORDER_CREATED.
// CLOSED는 "마감됨, 공동 주문 생성 대기"이고 order가 공동 주문을 만들었다는 이벤트를 받아야 ORDER_CREATED가 된다.
// 생성 실패 · 시간 초과면 CLOSED → FROZEN으로 돌아간다
// 서비스 사이 계약 값(ARCH-8): 이름을 바꾸거나 지우면 받는 서비스가 깨진다. 값은 추가만 하고, 설명 없이는 추가할 수 없다.
@Getter
@RequiredArgsConstructor
public enum SharedGroupPurchaseStatus {
    OPEN("모집 중"),
    FROZEN("마감 보류(판매 불가 상품이 있어 수정 대기)"),
    CLOSED("마감, 공동 주문 생성 대기"),
    ORDER_CREATED("공동 주문 생성됨"),
    CANCELED("취소");

    private final String description;
}
