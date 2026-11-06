package com.cartmate.cartmate.boundedContext.memberService.member.out;

// 관리자 대시보드 회원 집계 (한 번의 조회 결과)
public record MemberCounts(
        long total,
        long joinedToday,
        long buyers,
        long sellers,
        long suspended
) {
}