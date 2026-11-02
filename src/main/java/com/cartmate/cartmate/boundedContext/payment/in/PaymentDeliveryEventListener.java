package com.cartmate.cartmate.boundedContext.payment.in;

import com.cartmate.cartmate.boundedContext.payment.app.PaymentFacade;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;
import com.cartmate.cartmate.boundedContext.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.delivery.event.DeliveryClaimApprovedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 결제 후 환불은 판매자 승인으로만 처리한다.
// 판매자 승인과 같은 트랜잭션에서 환불하며, 환불이 실패하면(이미 정산된 결제 등) 승인도 함께 롤백된다.
@Component
@RequiredArgsConstructor
public class PaymentDeliveryEventListener {
    private final PaymentFacade paymentFacade;

    @EventListener
    public void handle(DeliveryClaimApprovedEvent event) {
        if (!"REFUND".equals(event.claimType()) || event.refundAmount() == null || event.refundAmount() <= 0) {
            return; // 교환은 환불 없음
        }

        PaymentType type = PaymentType.valueOf(event.purchaseType().name());
        if (type == PaymentType.SHARED_GROUP) {
            // 공동물품 구매는 확정 후 환불하지 않는다. 승인 자체를 막는다.
            throw new ServiceException(PaymentErrorCode.PAYMENT_REFUND_NOT_ALLOWED);
        }

        paymentFacade.refundByClaim(event.claimId(), event.orderId(), event.buyerMemberId(), event.refundAmount());
    }

}