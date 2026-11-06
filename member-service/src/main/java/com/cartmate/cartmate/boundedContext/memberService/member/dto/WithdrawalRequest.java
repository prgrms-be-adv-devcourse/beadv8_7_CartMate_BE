package com.cartmate.cartmate.boundedContext.memberService.member.dto;

import jakarta.validation.constraints.NotBlank;

// 탈퇴 신청. 로그인된 채 자리를 비운 사이 다른 사람이 탈퇴시키지 못하게 비밀번호를 다시 확인한다.
public record WithdrawalRequest(
        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {
}