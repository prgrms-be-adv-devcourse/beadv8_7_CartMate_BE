package com.cartmate.cartmate.boundedContext.member.dto;

import com.cartmate.cartmate.shared.member.dto.MemberDto;

public record LoginResult(
        MemberDto member,
        TokenPair tokens
) {
}