package com.cartmate.cartmate.boundedContext.payment.out;

import com.cartmate.cartmate.boundedContext.order.app.personal.OrderFacade;
import com.cartmate.cartmate.shared.order.dto.OrderPaymentInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// order 모듈의 개인 주문과 닿는 유일한 곳
@Component
@RequiredArgsConstructor
public class OrderFacadeClient implements PaymentOrderClient {
    private final OrderFacade orderFacade;

    @Override
    public OrderPaymentInfo getPaymentInfo(Long orderId, Long memberId, LocalDateTime now) {
        return orderFacade.getPaymentInfo(orderId, memberId, now);
    }

    @Override
    public void applyPayment(Long orderId, Long memberId, LocalDateTime paidAt) {
        orderFacade.applyPayment(orderId, memberId, paidAt);
    }
}