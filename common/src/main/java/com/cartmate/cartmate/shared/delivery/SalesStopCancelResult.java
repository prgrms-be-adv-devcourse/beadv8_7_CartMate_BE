package com.cartmate.cartmate.shared.delivery;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 판매글 내림으로 인한 취소 요청에 대한 배송 서버의 판정. 계약 값이라 추가만 한다.
 */
@Getter
@RequiredArgsConstructor
public enum SalesStopCancelResult {
    WHOLE_CANCELED("전체 취소", "주문의 배송 전부 취소 (공동구매만)"),
    SELLER_CANCELED("판매자 몫 취소", "그 판매자 몫의 배송만 취소"),
    REJECTED("취소 거절", "그 판매자 배송이 이미 발송돼 취소 안 함");

    private final String label;
    private final String description;
}
