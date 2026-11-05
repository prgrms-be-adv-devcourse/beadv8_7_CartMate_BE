package com.cartmate.cartmate.boundedContext.memberService.shared.member.event;

import com.cartmate.cartmate.boundedContext.memberService.shared.member.dto.MemberDto;
import com.cartmate.cartmate.global.event.DomainEvent;

// 회원 가입 시 발행. 지갑(지갑 생성)과 쿠폰(가입 쿠폰 자동 발급)이 받는다.
// Outbox로도 저장된다 (토픽 cartmate.member.member-signed-up).
public record MemberSignedUpEvent(MemberDto member) implements DomainEvent {
    @Override
    public String aggregateType() {
        return "Member";
    }
    @Override
    public String aggregateId() {
        return String.valueOf(member.id());
    }
}
