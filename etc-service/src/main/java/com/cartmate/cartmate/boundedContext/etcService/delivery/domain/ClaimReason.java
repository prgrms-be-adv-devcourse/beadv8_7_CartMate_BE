package com.cartmate.cartmate.boundedContext.etcService.delivery.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClaimReason {
    SIZE_MISMATCH("사이즈 불일치"),
    SIMPLE_CHANGE("단순 변심"),
    PRODUCT_DEFECT("상품 불량·파손"),
    WRONG_DELIVERY("오배송·누락"),
    OTHER("기타");

    private final String label;
}
