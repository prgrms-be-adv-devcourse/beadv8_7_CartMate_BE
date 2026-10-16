package com.cartmate.cartmate.boundedContext.post.domain;

public enum SalesPostSaveStatus {
    DRAFT("임시저장"),
    PUBLISHED("등록완료");

    private String name;

    SalesPostSaveStatus(String name) {
        this.name = name;
    }

    public boolean isVisible() {
        return this == PUBLISHED;
    }
}
