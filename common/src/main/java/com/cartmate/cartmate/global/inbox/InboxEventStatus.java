package com.cartmate.cartmate.global.inbox;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InboxEventStatus {
    NEW("수신"),
    PROCESSED("처리 완료"),
    FAILED("처리 실패");

    private final String label;
}
