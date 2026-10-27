package com.cartmate.cartmate.boundedContext.post.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum SalesPostUpdateStatus {
    NEW,              // 신규 생성
    COMPLETED,        // 수정 완료
    PENDING,          // 수정 진행중
    RECOVERING,       // 복구중
    RECOVERED,        // 복구 성공
    RECOVERY_FAILED;   // 복구 실패

    private static final Set<SalesPostUpdateStatus> EDITABLE =
            EnumSet.of(NEW, COMPLETED, RECOVERED, RECOVERY_FAILED);

    public static Set<SalesPostUpdateStatus> editableStatuses() {
        return Collections.unmodifiableSet(EDITABLE);
    }

    public boolean isEditable() {
        return EDITABLE.contains(this);
    }
}
