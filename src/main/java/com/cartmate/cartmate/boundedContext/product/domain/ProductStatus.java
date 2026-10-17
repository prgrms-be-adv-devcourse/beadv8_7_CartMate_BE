package com.cartmate.cartmate.boundedContext.product.domain;

public enum ProductStatus {
    ON_SALE("판매중"),
    PAUSED("판매 중지"), // 판매자가 직접 중지
    SUSPENDED("운영 중지"), // 관리자에 의한 판매자 정지
    CLOSED("판매 종료"),
    DELETED("삭제");

    private String name;

    ProductStatus(String name) {
        this.name = name;
    }

    public boolean isVisible() {
        return this == ON_SALE || this == CLOSED;
    }
}
