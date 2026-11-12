package com.cartmate.cartmate.shared.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SharedGroupOrderCancelReason {
    DEADLINE_EXPIRED("결제 기한 만료"),  // 결제 기한까지 전원 결제 못 함
    OUT_OF_STOCK("재고 부족"),
    CREATOR_CANCELED("개설자 취소"),
    SALES_STOPPED("판매 중지"),          // 판매글 판매 중지 · 숨김 · 삭제
    SELLER_CANCELED("판매자 사유 취소"); // 판매자 사유 취소로 모든 상품이 취소됨

    private final String label;
}
