package com.cartmate.cartmate.boundedContext.productService.post.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum SalesPostUpdateStatus {
    NEW("신규"),
    COMPLETED("수정 완료"),
    PENDING("수정 중"),
    RECOVERING("복구 중"),
    RECOVERED("복구 완료"),
    RECOVERY_FAILED("복구 실패");

    private final String label;

    private static final Set<SalesPostUpdateStatus> EDITABLE =
            EnumSet.of(NEW, COMPLETED, RECOVERED, RECOVERY_FAILED);

    public static Set<SalesPostUpdateStatus> editableStatuses() {
        return Collections.unmodifiableSet(EDITABLE);
    }

    public boolean isEditable() {
        return EDITABLE.contains(this);
    }
}
