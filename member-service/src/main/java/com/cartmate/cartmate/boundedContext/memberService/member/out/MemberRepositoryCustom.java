package com.cartmate.cartmate.boundedContext.memberService.member.out;

import java.time.LocalDateTime;

public interface MemberRepositoryCustom {

    // 전체·오늘 가입·구매자·판매자·정지 회원 수를 한 번의 조회로
    MemberCounts countForDashboard(LocalDateTime todayStart);
}