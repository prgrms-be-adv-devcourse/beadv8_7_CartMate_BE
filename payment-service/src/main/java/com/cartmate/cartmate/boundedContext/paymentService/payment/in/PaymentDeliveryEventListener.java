package com.cartmate.cartmate.boundedContext.paymentService.payment.in;

import com.cartmate.cartmate.boundedContext.paymentService.payment.app.PaymentFacade;
import com.cartmate.cartmate.boundedContext.paymentService.payment.domain.PaymentType;
import com.cartmate.cartmate.boundedContext.paymentService.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.lock.LockErrorCode;
import com.cartmate.cartmate.shared.delivery.event.DeliveryClaimApprovedEvent;
import com.cartmate.cartmate.shared.payment.event.ClaimRefundCompletedEvent;
import com.cartmate.cartmate.shared.payment.event.ClaimRefundFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class PaymentDeliveryEventListener {

    private static final Set<String> RETRYABLE_CODES = Set.of(
            LockErrorCode.LOCK_ACQUISITION_FAILED.resultCode(),
            PaymentErrorCode.PAYMENT_REFUND_IN_PROGRESS.resultCode());

    private final PaymentFacade paymentFacade;
    private final DomainEventPublisher eventPublisher;

    @EventListener
    public void handle(DeliveryClaimApprovedEvent event) {
        if (!"REFUND".equals(event.claimType()) || event.refundAmount() == null || event.refundAmount() <= 0) {
            return;
        }

        try {
            refund(event);
        } catch (ServiceException e) {
            if (RETRYABLE_CODES.contains(e.getResultCode())) {
                throw e;
            }
            eventPublisher.publish(new ClaimRefundFailedEvent(event.claimId(), event.orderId(), e.getResultCode()));
            return;
        }

        eventPublisher.publish(new ClaimRefundCompletedEvent(event.claimId(), event.orderId(), event.refundAmount()));
    }

    private void refund(DeliveryClaimApprovedEvent event) {
        PaymentType type = PaymentType.valueOf(event.purchaseType().name());
        if (type == PaymentType.SHARED_GROUP) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_REFUND_NOT_ALLOWED);
        }
        paymentFacade.refundByClaim(event.claimId(), event.orderId(), event.buyerMemberId(), event.refundAmount());
    }
}
