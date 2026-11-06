package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.support.TestCatalog;
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
public class FakeOrderMemberClient implements OrderMemberClient {

    @Override
    public Map<Long, String> findNicknames(Collection<Long> memberIds) {
        Map<Long, String> nicknames = new HashMap<>();
        memberIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(memberId -> nicknames.put(memberId, TestCatalog.nickname(memberId)));
        return nicknames;
    }
}
