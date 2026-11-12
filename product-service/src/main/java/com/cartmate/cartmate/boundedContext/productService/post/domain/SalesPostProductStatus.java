package com.cartmate.cartmate.boundedContext.productService.post.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SalesPostProductStatus {
    ON_SALE("판매 중"),
    PAUSED("판매 중지"),     // 판매자가 직접 중지
    SUSPENDED("운영 중지"),  // 관리자에 의한 판매자 정지
    CLOSED("판매 종료"),
    DELETED("삭제"),
    ACTIVE("활성"),
    INACTIVE("비활성"),
    PENDING("등록 중");

    private final String label;

    // 판매자 내림·관리자 숨김·재개처럼 판매글 단위로 서로 바꿀 수 있는 '판매 관련' 상태인지.
    // 삭제·등록 중·종료 같은 상태는 판매 중지 흐름으로 바꾸거나 되돌리지 않는다.
    public boolean isSaleControllable() {
        return this == ON_SALE || this == PAUSED || this == SUSPENDED;
    }
}
