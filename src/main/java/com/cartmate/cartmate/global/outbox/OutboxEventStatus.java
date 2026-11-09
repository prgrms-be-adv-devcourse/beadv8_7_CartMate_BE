package com.cartmate.cartmate.global.outbox;

public enum OutboxEventStatus {
    NEW,        // 저장됨, 아직 릴레이 안 됨
    PUBLISHED,  // 싱크 전달 성공
    FAILED      // 최대 시도 횟수 초과. 사람이 보고 처리한다
}
