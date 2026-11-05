package com.cartmate.cartmate.boundedContext.memberService.member.dto;

import com.cartmate.cartmate.boundedContext.memberService.shared.member.dto.MemberDto;

public record LoginResult(
        MemberDto member,
        TokenPair tokens
) {
}