package com.cartmate.cartmate.global.outbox;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OutboxEventStatus {
    NEW("발행 대기"),
    PUBLISHED("발행 완료"),
    FAILED("발행 실패");

    private final String label;
}
