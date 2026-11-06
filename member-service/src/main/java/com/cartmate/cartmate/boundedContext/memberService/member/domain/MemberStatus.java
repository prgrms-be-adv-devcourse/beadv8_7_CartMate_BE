package com.cartmate.cartmate.boundedContext.memberService.member.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberStatus {
    ACTIVE("정상"),
    SUSPENDED("이용 정지"),
    WITHDRAWAL_PENDING("탈퇴 대기"),   // 탈퇴 신청 후 유예 기간. 로그인·조회는 되고 주문 등 변경은 막힌다
    WITHDRAWN("탈퇴");

    private final String description;
}