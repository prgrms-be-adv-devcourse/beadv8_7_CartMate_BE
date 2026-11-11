package com.cartmate.cartmate.boundedContext.productService.post.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SalesPostSaleStatus {
    ON_SALE("판매 중"),
    PAUSED("판매 중지"),     // 판매자가 직접 중지
    SUSPENDED("운영 중지"),  // 관리자에 의한 판매자 정지
    CLOSED("판매 종료"),
    DELETED("삭제"),
    DELETING("삭제 중"),
    PENDING("등록 중");

    private final String label;
}
