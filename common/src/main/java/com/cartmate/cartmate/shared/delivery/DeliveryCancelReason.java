package com.cartmate.cartmate.shared.delivery;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 배송과 주문 항목이 왜 취소됐는지. 서비스 사이 계약 값이라 추가만 한다.
 * sellerReason이 true인 값은 판매자가 직접 취소할 때 고르는 사유다.
 */
@Getter
@RequiredArgsConstructor
public enum DeliveryCancelReason {
    BUYER_CANCELED("구매자 취소", false),
    SALES_STOPPED("판매 중지", false),
    SELLER_OUT_OF_STOCK("품절·재고 부족", true),
    SELLER_PRODUCT_ISSUE("상품 문제 발견", true),
    SELLER_OTHER("기타 판매자 사정", true);

    private final String description;
    private final boolean sellerReason;
}
