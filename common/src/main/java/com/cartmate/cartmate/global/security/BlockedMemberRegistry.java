package com.cartmate.cartmate.global.security;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 정지된 회원 ID 목록. 남은 액세스 토큰도 즉시 막기 위해 필터가 매 요청 확인한다.
// 서버 메모리라 재시작 시 DB에서 다시 불러온다 (BlockedMemberSync).
// 서버가 여러 대가 되면 Redis 같은 공용 저장소로 옮겨야 한다.
@Component
public class BlockedMemberRegistry {
    private final Set<Long> blocked = ConcurrentHashMap.newKeySet();

    public boolean isBlocked(Long memberId) {
        return blocked.contains(memberId);
    }

    public void block(Long memberId) {
        blocked.add(memberId);
    }

    public void unblock(Long memberId) {
        blocked.remove(memberId);
    }

    public void replaceAll(Collection<Long> memberIds) {
        blocked.clear();
        blocked.addAll(memberIds);
    }
}