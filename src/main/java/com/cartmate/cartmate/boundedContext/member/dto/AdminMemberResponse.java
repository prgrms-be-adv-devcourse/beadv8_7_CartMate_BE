package com.cartmate.cartmate.boundedContext.member.dto;

import com.cartmate.cartmate.boundedContext.member.domain.Member;

import java.time.LocalDateTime;

public record AdminMemberResponse(
        Long id,
        String email,
        String nickname,
        String role,
        String status,
        LocalDateTime joinedAt
) {
    public static AdminMemberResponse from(Member member) {
        return new AdminMemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getRole().name(),
                member.getStatus().name(),
                member.getCreateDate()
        );
    }
}