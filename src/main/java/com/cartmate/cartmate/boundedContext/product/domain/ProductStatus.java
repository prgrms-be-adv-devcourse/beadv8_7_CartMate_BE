package com.cartmate.cartmate.boundedContext.product.domain;

public enum ProductStatus {
    ON_SALE,    // 판매중
    PAUSED,     // 판매 중지 (판매자가 직접 중지)
    SUSPENDED,  // 운영 중지 (관리자에 의한 판매자 정지)
    CLOSED,     // 판매 종료
    DELETED,    // 삭제
    ACTIVE,     // 활성화
    INACTIVE,   // 비활성화
    PENDING     // 등록 진행중
}
