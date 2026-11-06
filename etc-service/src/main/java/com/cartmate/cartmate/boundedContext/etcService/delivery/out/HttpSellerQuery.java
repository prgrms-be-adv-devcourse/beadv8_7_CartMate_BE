package com.cartmate.cartmate.boundedContext.etcService.delivery.out;

import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalResponse;
import com.cartmate.cartmate.shared.member.SellerQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Optional;

// 회원 서버 내부 API(/internal/members/{id}/seller-id)로 판매자 ID를 묻는다. 회원 서버가 따로 떠 있어 HTTP로만 부른다.
@Component
@RequiredArgsConstructor
public class HttpSellerQuery implements SellerQuery {

    private static final ParameterizedTypeReference<InternalResponse<Long>> SELLER_ID = new ParameterizedTypeReference<>() {};

    private final InternalApiClient memberServerApiClient;

    @Override
    public Optional<Long> findSellerIdByMemberId(Long memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(memberServerApiClient.get("/internal/members/{m}/seller-id", SELLER_ID, memberId));
    }
}
