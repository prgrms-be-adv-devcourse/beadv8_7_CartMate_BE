package com.cartmate.cartmate.boundedContext.memberService.member.out;

import com.cartmate.cartmate.boundedContext.memberService.member.out.MyPageActivityClient.IndividualPurchase;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MyPageActivityClient.Order;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MyPageActivityClient.SharedOrder;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MyPageActivityClient.SharedPurchase;
import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;

// 주문 서버 내부 API를 HTTP로 부른다. 통신 공통 처리(시간 제한, 오류 변환, 연결 실패 503)는 InternalApiClient가 한다.
@Component
@RequiredArgsConstructor
public class HttpMemberOrderServerClient implements MemberOrderServerClient {

    private final InternalApiClient orderServerApiClient;

    @Override
    public List<SharedPurchase> getSharedPurchases(Long memberId) {
        return list(orderServerApiClient.get("/internal/group-purchases/shared?memberId={m}",
                new ParameterizedTypeReference<InternalResponse<List<SharedPurchase>>>() {}, memberId));
    }

    @Override
    public List<IndividualPurchase> getIndividualPurchases(Long memberId) {
        return list(orderServerApiClient.get("/internal/group-purchases/individual?memberId={m}",
                new ParameterizedTypeReference<InternalResponse<List<IndividualPurchase>>>() {}, memberId));
    }

    @Override
    public List<Order> getOrders(Long memberId) {
        return list(orderServerApiClient.get("/internal/orders?memberId={m}",
                new ParameterizedTypeReference<InternalResponse<List<Order>>>() {}, memberId));
    }

    @Override
    public List<SharedOrder> getSharedOrders(Long memberId) {
        return list(orderServerApiClient.get("/internal/group-orders/shared?memberId={m}",
                new ParameterizedTypeReference<InternalResponse<List<SharedOrder>>>() {}, memberId));
    }

    @Override
    public long countActiveGroupPurchases(Long memberId) {
        return count(orderServerApiClient.get("/internal/group-purchases/active-count?memberId={m}",
                new ParameterizedTypeReference<InternalResponse<Long>>() {}, memberId));
    }

    @Override
    public long countPaymentPendingOrders(Long memberId) {
        return count(orderServerApiClient.get("/internal/orders/payment-pending-count?memberId={m}",
                new ParameterizedTypeReference<InternalResponse<Long>>() {}, memberId));
    }

    @Override
    public List<Long> findConfirmedSharedOrderIds(Long memberId) {
        return list(orderServerApiClient.get("/internal/group-orders/shared/confirmed-ids?memberId={m}",
                new ParameterizedTypeReference<InternalResponse<List<Long>>>() {}, memberId));
    }

    @Override
    public long countPayingSharedOrders(Long memberId) {
        return count(orderServerApiClient.get("/internal/group-orders/shared/paying-count?memberId={m}",
                new ParameterizedTypeReference<InternalResponse<Long>>() {}, memberId));
    }

    private static <T> List<T> list(List<T> value) {
        return value == null ? List.of() : value;
    }

    private static long count(Long value) {
        return value == null ? 0L : value;
    }
}
