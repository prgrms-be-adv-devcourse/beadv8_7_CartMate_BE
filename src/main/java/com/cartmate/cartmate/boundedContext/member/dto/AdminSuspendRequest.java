package com.cartmate.cartmate.boundedContext.member.dto;

import com.cartmate.cartmate.boundedContext.member.domain.SuspensionDuration;
import com.cartmate.cartmate.boundedContext.member.domain.SuspensionReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminSuspendRequest(
        @NotNull(message = "정지 사유를 선택해주세요.")
        SuspensionReason reasonType,

        @NotBlank(message = "상세 사유를 입력해주세요.")
        @Size(max = 300, message = "상세 사유는 300자 이하로 입력해주세요.")
        String reasonDetail,

        @NotNull(message = "정지 기간을 선택해주세요.")
        SuspensionDuration duration
) {
}