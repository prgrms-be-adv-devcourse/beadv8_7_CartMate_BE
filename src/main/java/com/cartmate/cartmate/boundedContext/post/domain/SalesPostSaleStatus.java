package com.cartmate.cartmate.boundedContext.post.domain;

public enum SalesPostSaleStatus {
    ON_SALE("판매중"),
    PAUSED("판매 중지"), // 판매자가 직접 중지
    SUSPENDED("운영 중지"), // 관리자에 의한 판매자 정지
    CLOSED("판매 종료");

    private String name;

    SalesPostSaleStatus(String name) {
        this.name = name;
    }

    public boolean isVisible() {
        return this == ON_SALE || this == CLOSED;
    }
}
