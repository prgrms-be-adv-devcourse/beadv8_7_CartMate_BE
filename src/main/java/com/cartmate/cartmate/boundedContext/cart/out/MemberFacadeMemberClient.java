package com.cartmate.cartmate.boundedContext.cart.out;

import com.cartmate.cartmate.shared.member.MemberQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Component
@RequiredArgsConstructor
class MemberFacadeMemberClient implements MemberClient {

    // MemberFacade 대신 shared 조회 창구를 쓴다 (member ↔ cart 순환 참조 방지)
    private final MemberQuery memberQuery;

    @Override
    public Map<Long, String> findNicknames(Collection<Long> memberIds) {
        return memberQuery.findNicknames(memberIds);
    }
}
