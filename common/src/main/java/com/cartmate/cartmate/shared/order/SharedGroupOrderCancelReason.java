package com.cartmate.cartmate.shared.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 공동 주문이 취소된 이유. 취소 이벤트로 배송 · 결제 · 어뷰징에 나간다
// 서비스 사이 계약 값(ARCH-8): 이름을 바꾸거나 지우면 받는 서비스가 깨진다. 값은 추가만 하고, 설명 없이는 추가할 수 없다.
@Getter
@RequiredArgsConstructor
public enum SharedGroupOrderCancelReason {
    DEADLINE_EXPIRED("결제 기한까지 전원 결제 못 함"),
    OUT_OF_STOCK("재고 부족"),
    CREATOR_CANCELED("개설자 취소"),
    SALES_STOPPED("판매글 판매 중지 · 숨김 · 삭제");

    private final String description;
}
