package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.boundedContext.paymentService.payment.app.PaymentFacade;
import com.cartmate.cartmate.boundedContext.paymentService.shared.payment.dto.PaymentOrderType;
import com.cartmate.cartmate.boundedContext.paymentService.shared.payment.dto.PaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

// 같은 앱 안에서는 결제 Facade를 직접 부른다 (주문 → 결제 한 방향). 결제 서버 분리 시 HTTP 구현으로 교체.
@Component
@ConditionalOnProperty(name = "cartmate.payment.client", havingValue = "local", matchIfMissing = true)
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

    // 같은 트랜잭션이라 주문 반영이 실패하면 차감도 함께 롤백된다 → 되돌릴 것이 없다
    @Override
    public void compensateCharge(PaymentOrderType orderType, Long orderId, Long memberId) {
    }
}