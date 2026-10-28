package com.cartmate.cartmate.boundedContext.payment.in;

import com.cartmate.cartmate.boundedContext.payment.app.PaymentFacade;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 결제 후 환불은 판매자 승인(배송 클레임)으로만 처리한다.
// 예외: 공동물품 구매 무산은 판매자 승인 대상이 아니라 시스템이 자동 환불한다.
// 예외: 판매자 확인 전(배송요청 상태)에 구매자가 주문을 취소하면 전액 자동 환불한다.
@Component
@RequiredArgsConstructor
public class PaymentOrderEventListener {
    private final PaymentFacade paymentFacade;

    // 배송 리스너가 먼저 실행되어 판매자 확인 여부를 검증한 뒤에 환불한다. 결제 전 취소(paid=false)는 환불할 결제가 없다.
    @EventListener
    public void handle(OrderCanceledEvent event) {
        if (!event.paid()) {
            return;
        }
        paymentFacade.refundByBuyerCancel(event.orderId(), event.memberId());
    }

    @EventListener
    public void handle(SharedGroupOrderCanceledEvent event) {
        paymentFacade.refundCanceledSharedGroupOrder(event.sharedGroupOrderId(), event.cancelReason());
    }
}