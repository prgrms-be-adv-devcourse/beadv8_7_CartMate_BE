package com.cartmate.cartmate.shared.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 개인 주문 · 개인물품 공동구매 주문이 취소된 이유. 취소 이벤트로 배송 · 결제 · cart에 나간다
// 서비스 사이 계약 값(ARCH-8): 이름을 바꾸거나 지우면 받는 서비스가 깨진다. 값은 추가만 하고, 설명 없이는 추가할 수 없다.
@Getter
@RequiredArgsConstructor
public enum OrderCancelReason {
    BUYER_CANCELED("구매자 취소"),
    PAYMENT_EXPIRED("결제 기한 만료"),
    OUT_OF_STOCK("재고 부족"),
    SALES_STOPPED("판매글 판매 중지 · 숨김 · 삭제"),
    GROUP_PURCHASE_CLOSED("개인물품 공동구매가 닫힘(결제 마감 = 판매 마감)");

    private final String description;
}
