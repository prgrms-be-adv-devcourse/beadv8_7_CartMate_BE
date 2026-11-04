package com.cartmate.cartmate.boundedContext.order.out;

import com.cartmate.cartmate.boundedContext.payment.app.PaymentFacade;
import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

// 같은 앱 안에서는 결제 Facade를 직접 부른다 (주문 → 결제 한 방향). 결제 서버 분리 시 HTTP 구현으로 교체.
@Component
@RequiredArgsConstructor
public class PaymentFacadeOrderPaymentClient implements OrderPaymentClient {

    private final PaymentFacade paymentFacade;

    @Override
    public PaymentResult charge(PaymentOrderType orderType, Long orderId, Long memberId, long amount) {
        return paymentFacade.charge(orderType, orderId, memberId, amount);
    }

    @Override
    public Optional<PaymentResult> findPayment(PaymentOrderType orderType, Long orderId, Long memberId) {
        return paymentFacade.findPayment(orderType, orderId, memberId);
    }

    @Override
    public long getWalletBalance(Long memberId) {
        return paymentFacade.getWalletBalance(memberId);
    }
}