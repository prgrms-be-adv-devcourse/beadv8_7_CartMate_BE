package com.cartmate.cartmate.global.security;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 탈퇴 대기 중인 회원 목록 (정지 회원 BlockedMemberRegistry와 같은 방식).
 * 탈퇴 대기 회원은 조회와 탈퇴 관련 요청만 할 수 있다. 상태는 바뀌는 값이라 토큰에 넣지 않고 여기서 관리한다.
 */
@Component
public class WithdrawalPendingRegistry {
    private final Set<Long> pending = ConcurrentHashMap.newKeySet();

    public boolean isPending(Long memberId) {
        return pending.contains(memberId);
    }

    public void add(Long memberId) {
        pending.add(memberId);
    }

    public void remove(Long memberId) {
        pending.remove(memberId);
    }

    public void replaceAll(Collection<Long> memberIds) {
        pending.clear();
        pending.addAll(memberIds);
    }
}