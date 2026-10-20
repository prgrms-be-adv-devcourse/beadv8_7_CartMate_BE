package com.cartmate.cartmate.boundedContext.order.out;

import com.cartmate.cartmate.boundedContext.member.app.MemberFacade;
import com.cartmate.cartmate.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
class MemberFacadeOrderMemberClient implements OrderMemberClient {

    private final MemberFacade memberFacade;

    @Override
    public Map<Long, String> findNicknames(Collection<Long> memberIds) {
        Map<Long, String> nicknames = new HashMap<>();
        memberIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(memberId -> nicknames.put(memberId,
                        memberFacade.findById(memberId).map(MemberDto::nickname).orElse(null)));
        return nicknames;
    }
}
