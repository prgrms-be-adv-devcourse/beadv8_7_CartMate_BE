package com.cartmate.cartmate.shared.member.dto;

public record MemberDto(
        Long id,
        String email,
        String nickname,
        String role
) {
}
