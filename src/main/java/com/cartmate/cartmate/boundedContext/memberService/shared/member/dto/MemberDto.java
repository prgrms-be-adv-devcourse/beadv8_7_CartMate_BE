package com.cartmate.cartmate.boundedContext.memberService.shared.member.dto;

public record MemberDto(
        Long id,
        String email,
        String nickname,
        String role
) {
}
