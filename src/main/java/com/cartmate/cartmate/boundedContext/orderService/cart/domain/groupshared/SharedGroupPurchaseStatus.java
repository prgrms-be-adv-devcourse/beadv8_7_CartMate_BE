package com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared;

// OPEN → (FROZEN) → CLOSED → ORDER_CREATED. CLOSED는 "마감됨, 공동 주문 생성 대기"이고
// order가 공동 주문을 만들었다는 이벤트를 받아야 ORDER_CREATED가 된다. 생성 실패 · 시간 초과면 CLOSED → FROZEN으로 돌아간다
public enum SharedGroupPurchaseStatus {
    OPEN,
    FROZEN,
    CLOSED,
    ORDER_CREATED,
    CANCELED
}
