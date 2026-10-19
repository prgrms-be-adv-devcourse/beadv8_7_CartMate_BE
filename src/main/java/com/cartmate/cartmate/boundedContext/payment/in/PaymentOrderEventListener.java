package com.cartmate.cartmate.boundedContext.payment.in;

import com.cartmate.cartmate.boundedContext.payment.app.PaymentFacade;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentOrderEventListener {
    private final PaymentFacade paymentFacade;

    // 공동 주문 취소와 같은 트랜잭션에서 환불한다.
    // 환불이 실패하면 취소도 롤백되고, order 스케줄러가 다음 주기에 다시 시도한다.
    @EventListener
    public void handle(SharedGroupOrderCanceledEvent event) {
        paymentFacade.refundSharedGroupOrder(event.sharedGroupOrderId(), event.cancelReason());
    }
}