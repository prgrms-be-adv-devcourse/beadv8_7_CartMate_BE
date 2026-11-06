package com.cartmate.cartmate.shared.member;

import java.util.Collection;
import java.util.Map;

// 다른 모듈이 회원 정보를 조회하는 창구. MemberFacade 대신 이걸 의존하면 순환 참조가 생기지 않는다.
public interface MemberQuery {

    // 회원 ID별 닉네임. 없는 회원은 값이 null
    Map<Long, String> findNicknames(Collection<Long> memberIds);
}