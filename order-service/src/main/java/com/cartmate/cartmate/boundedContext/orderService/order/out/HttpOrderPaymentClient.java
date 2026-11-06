package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalResponse;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalServerUnavailableException;
import com.cartmate.cartmate.shared.payment.dto.PaymentChargeRequest;
import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@ConditionalOnProperty(name = "cartmate.payment.client", havingValue = "http")
@RequiredArgsConstructor
public class HttpOrderPaymentClient implements OrderPaymentClient {

    private static final String PATH = "/internal/payments";
    private static final ParameterizedTypeReference<InternalResponse<PaymentResult>> RESULT = new ParameterizedTypeReference<>() {};

    private final InternalApiClient paymentServerApiClient;

    @Override
    public PaymentResult charge(PaymentOrderType orderType, Long orderId, Long memberId, long amount) {
        try {
            return paymentServerApiClient.post(PATH, new PaymentChargeRequest(orderType, orderId, memberId, amount), RESULT);
        } catch (InternalServerUnavailableException e) {
            log.warn("결제 차감 응답 없음, 결제 여부 확인: {} #{} 회원 #{}", orderType, orderId, memberId);
            return findPayment(orderType, orderId, memberId)
                    .filter(PaymentResult::isPaid)
                    .orElseThrow(() -> e);
        }
    }

    @Override
    public Optional<PaymentResult> findPayment(PaymentOrderType orderType, Long orderId, Long memberId) {
        try {
            return Optional.of(paymentServerApiClient.get(PATH + "?orderType={t}&orderId={o}&memberId={m}",
                    RESULT, orderType, orderId, memberId));
        } catch (InternalServerUnavailableException e) {
            throw e;
        } catch (ServiceException e) {
            if (InternalApiClient.isNotFound(e)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public long getWalletBalance(Long memberId) {
        Long balance = paymentServerApiClient.get(PATH + "/wallet-balance?memberId={m}",
                new ParameterizedTypeReference<InternalResponse<Long>>() {}, memberId);
        return balance == null ? 0 : balance;
    }

    @Override
    public void compensateCharge(PaymentOrderType orderType, Long orderId, Long memberId) {
        try {
            paymentServerApiClient.post(PATH + "/compensate?orderType={t}&orderId={o}&memberId={m}", null,
                    new ParameterizedTypeReference<InternalResponse<Void>>() {}, orderType, orderId, memberId);
        } catch (RuntimeException e) {
            log.error("결제 되돌리기 실패 — 수동 확인 필요: {} #{} 회원 #{}", orderType, orderId, memberId, e);
        }
    }
}