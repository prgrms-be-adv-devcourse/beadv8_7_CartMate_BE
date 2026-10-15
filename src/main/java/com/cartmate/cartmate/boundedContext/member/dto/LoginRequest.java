package com.cartmate.cartmate.boundedContext.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password,

        boolean remember // 2단계(로그인 상태 유지)에서 사용
) {
}