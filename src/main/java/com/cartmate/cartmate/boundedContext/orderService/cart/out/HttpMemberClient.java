package com.cartmate.cartmate.boundedContext.orderService.cart.out;

import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

// 회원 서버 내부 API(/internal/members/nicknames)를 HTTP로 부른다. 없는 회원은 값이 null로 온다.
@Component
@RequiredArgsConstructor
class HttpMemberClient implements MemberClient {

    static final String PATH = "/internal/members/nicknames?ids={ids}";
    static final ParameterizedTypeReference<InternalResponse<Map<Long, String>>> NICKNAMES =
            new ParameterizedTypeReference<>() {};

    private final InternalApiClient memberServerApiClient;

    @Override
    public Map<Long, String> findNicknames(Collection<Long> memberIds) {
        String joined = memberIds == null ? "" : memberIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        if (joined.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> nicknames = memberServerApiClient.get(PATH, NICKNAMES, joined);
        return nicknames == null ? Map.of() : nicknames;
    }
}
