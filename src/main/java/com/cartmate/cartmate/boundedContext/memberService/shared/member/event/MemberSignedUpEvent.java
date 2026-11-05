package com.cartmate.cartmate.boundedContext.memberService.shared.member.event;

import com.cartmate.cartmate.boundedContext.memberService.shared.member.dto.MemberDto;

public record MemberSignedUpEvent(MemberDto member) {
}
