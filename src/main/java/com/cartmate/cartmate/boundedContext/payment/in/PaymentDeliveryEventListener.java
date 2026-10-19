package com.cartmate.cartmate.boundedContext.payment.in;

import com.cartmate.cartmate.boundedContext.payment.app.PaymentFacade;
import com.cartmate.cartmate.shared.delivery.event.DeliveryClaimApprovedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentDeliveryEventListener {
    private final PaymentFacade paymentFacade;

    // 판매자 승인과 같은 트랜잭션에서 환불한다. 환불이 실패하면 승인도 함께 취소된다.
    @EventListener
    public void handle(DeliveryClaimApprovedEvent event) {
        if (!"REFUND".equals(event.claimType()) || event.refundAmount() <= 0) {
            return; // 교환은 환불 없음
        }
        paymentFacade.refundByClaim(event.claimId(), event.orderId(), event.memberId(), event.refundAmount());
    }
}