package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class HttpOrderMemberClient implements OrderMemberClient {

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
