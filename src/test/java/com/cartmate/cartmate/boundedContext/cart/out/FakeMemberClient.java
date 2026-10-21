package com.cartmate.cartmate.boundedContext.cart.out;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
@Primary
@Profile("test")
public class FakeMemberClient implements MemberClient {

    @Override
    public Map<Long, String> findNicknames(Collection<Long> memberIds) {
        Map<Long, String> nicknames = new HashMap<>();
        memberIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(memberId -> nicknames.put(memberId, "회원" + memberId));
        return nicknames;
    }
}
