package com.cartmate.cartmate.boundedContext.memberService.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MfaVerifyRequest(
        @NotBlank(message = "인증 정보가 없습니다. 다시 로그인해주세요.")
        String challengeId,

        // 숫자 6자리 (메일·OTP 앱) 또는 관리자 백업 코드 (ABCD-EFGH-2345)
        @NotBlank(message = "인증번호는 필수입니다.")
        @Pattern(regexp = "^(\\d{6}|[A-Za-z0-9]{4}-?[A-Za-z0-9]{4}-?[A-Za-z0-9]{4})$", message = "인증번호 형식이 올바르지 않습니다.")
        String code,

        boolean trustDevice      // "이 기기 30일 기억"
) {
}
