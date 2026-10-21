package com.cartmate.cartmate.boundedContext.payment.in;

import com.cartmate.cartmate.boundedContext.payment.app.PaymentFacade;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 결제 후 환불은 판매자 승인(배송 클레임)으로만 처리한다.
// 예외: 공동물품 구매 무산은 판매자 승인 대상이 아니라 시스템이 자동 환불한다.
@Component
@RequiredArgsConstructor
public class PaymentOrderEventListener {
    private final PaymentFacade paymentFacade;

    @EventListener
    public void handle(SharedGroupOrderCanceledEvent event) {
        paymentFacade.refundCanceledSharedGroupOrder(event.sharedGroupOrderId(), event.cancelReason());
    }
}