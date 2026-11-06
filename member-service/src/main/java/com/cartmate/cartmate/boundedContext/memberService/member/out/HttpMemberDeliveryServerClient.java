package com.cartmate.cartmate.boundedContext.memberService.member.out;

import com.cartmate.cartmate.boundedContext.memberService.member.out.MyPageActivityClient.Delivery;
import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

// 배송 서버 내부 API를 HTTP로 부른다. 통신 공통 처리(시간 제한, 오류 변환, 연결 실패 503)는 InternalApiClient가 한다.
@Component
@RequiredArgsConstructor
public class HttpMemberDeliveryServerClient implements MemberDeliveryServerClient {

    private static final ParameterizedTypeReference<InternalResponse<Long>> COUNT = new ParameterizedTypeReference<>() {};

    private final InternalApiClient etcServerApiClient;

    @Override
    public List<Delivery> getMyDeliveries(Long memberId) {
        List<Delivery> deliveries = etcServerApiClient.get("/internal/deliveries?memberId={m}",
                new ParameterizedTypeReference<InternalResponse<List<Delivery>>>() {}, memberId);
        return deliveries == null ? List.of() : deliveries;
    }

    @Override
    public long countUnfinishedOfBuyer(Long memberId) {
        return count(etcServerApiClient.get("/internal/deliveries/unfinished-count?memberId={m}", COUNT, memberId));
    }

    @Override
    public long countUnfinishedOfSharedOrders(List<Long> sharedGroupOrderIds) {
        if (sharedGroupOrderIds == null || sharedGroupOrderIds.isEmpty()) {
            return 0L; // 확정된 공동 주문이 없으면 물을 것도 없다
        }
        String ids = sharedGroupOrderIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return count(etcServerApiClient.get("/internal/deliveries/unfinished-count/shared-orders?ids={ids}", COUNT, ids));
    }

    @Override
    public long countClaimsInProgressOfBuyer(Long memberId) {
        return count(etcServerApiClient.get("/internal/deliveries/claims-in-progress-count?memberId={m}", COUNT, memberId));
    }

    @Override
    public long countUnfinishedOfSeller(Long sellerId) {
        return count(etcServerApiClient.get("/internal/deliveries/seller/unfinished-count?sellerId={s}", COUNT, sellerId));
    }

    @Override
    public long countClaimsInProgressOfSeller(Long sellerId) {
        return count(etcServerApiClient.get("/internal/deliveries/seller/claims-in-progress-count?sellerId={s}", COUNT, sellerId));
    }

    private static long count(Long value) {
        return value == null ? 0L : value;
    }
}
