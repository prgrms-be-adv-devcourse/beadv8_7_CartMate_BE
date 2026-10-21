package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.out.MemberRepository;
import com.cartmate.cartmate.shared.member.MemberQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// 리포지토리만 의존한다. MemberFacade를 의존하면 마이페이지 → 다른 모듈 → 다시 member로 순환이 생긴다.
@Component
@RequiredArgsConstructor
public class MemberQueryAdapter implements MemberQuery {
    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findNicknames(Collection<Long> memberIds) {
        List<Long> ids = memberIds.stream().filter(Objects::nonNull).distinct().toList();

        Map<Long, String> nicknames = new HashMap<>();
        ids.forEach(id -> nicknames.put(id, null)); // 없는 회원도 키는 남긴다 (기존 동작과 같게)
        memberRepository.findAllById(ids).forEach(m -> nicknames.put(m.getId(), m.getNickname()));
        return nicknames;
    }
}