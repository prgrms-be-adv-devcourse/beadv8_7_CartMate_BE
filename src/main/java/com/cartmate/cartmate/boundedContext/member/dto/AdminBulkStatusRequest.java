package com.cartmate.cartmate.boundedContext.member.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminBulkStatusRequest(
        @NotEmpty(message = "회원을 선택해주세요.")
        @Size(max = 100, message = "한 번에 100명까지 처리할 수 있습니다.")
        List<Long> memberIds,

        @Pattern(regexp = "SUSPEND|ACTIVATE", message = "action은 SUSPEND 또는 ACTIVATE입니다.")
        String action,

        @Valid
        AdminSuspendRequest suspend // action이 SUSPEND일 때만 사용
) {
}