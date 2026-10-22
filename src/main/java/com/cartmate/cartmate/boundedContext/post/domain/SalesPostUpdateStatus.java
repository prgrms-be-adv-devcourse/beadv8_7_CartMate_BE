package com.cartmate.cartmate.boundedContext.post.domain;

public enum SalesPostUpdateStatus {
    NEW,              // 신규 생성
    PENDING,          // 수정 진행중
    COMPLETED,        // 수정 완료
    RECOVERING,       // 복구중
    RECOVERED,        // 복구 성공
    RECOVERY_FAILED   // 복구 실패

}
