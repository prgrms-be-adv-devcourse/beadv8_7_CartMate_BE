package com.cartmate.cartmate.boundedContext.memberService.member.out;

import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// 결제 서버 내부 API를 HTTP로 부른다. 통신 공통 처리(시간 제한, 오류 변환, 연결 실패 503)는 InternalApiClient가 한다.
@Component
@ConditionalOnProperty(name = "cartmate.payment.client", havingValue = "http")
@RequiredArgsConstructor
public class HttpMemberPaymentServerClient implements MemberPaymentServerClient {

    private final InternalApiClient paymentServerApiClient;

    @Override
    public PaymentStats getDailyPaymentStats(LocalDate date) {
        return paymentServerApiClient.get("/internal/payments/daily-summary?date={d}",
                new ParameterizedTypeReference<InternalResponse<PaymentStats>>() {}, date);
    }

    @Override
    public long getWalletBalance(Long memberId) {
        Long balance = paymentServerApiClient.get("/internal/payments/wallet-balance?memberId={m}",
                new ParameterizedTypeReference<InternalResponse<Long>>() {}, memberId);
        return balance == null ? 0L : balance;
    }

    @Override
    public long countPendingPayouts(Long sellerId) {
        Long count = paymentServerApiClient.get("/internal/payouts/pending-count?sellerId={s}",
                new ParameterizedTypeReference<InternalResponse<Long>>() {}, sellerId);
        return count == null ? 0L : count;
    }
}