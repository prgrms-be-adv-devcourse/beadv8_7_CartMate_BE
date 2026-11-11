package com.cartmate.cartmate.boundedContext.memberService.member.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaResendRequest(
        @NotBlank(message = "인증 정보가 없습니다. 다시 로그인해주세요.")
        String challengeId
) {
}
