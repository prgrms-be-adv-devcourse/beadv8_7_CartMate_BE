package com.cartmate.cartmate.shared.member.event;

import com.cartmate.cartmate.shared.member.dto.MemberDto;

public record MemberSignedUpEvent(MemberDto member) {
}
