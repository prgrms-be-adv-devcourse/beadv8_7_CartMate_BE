package com.cartmate.cartmate.boundedContext.payment.app;

import com.cartmate.cartmate.boundedContext.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentRefundReason;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentResponse;
import com.cartmate.cartmate.boundedContext.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentCancelUseCase {
    private final PaymentRepository paymentRepository;
    private final PaymentRefundUseCase paymentRefundUseCase;

    // 배송 전 구매 취소: 남은 금액 전부 환불
    public PaymentResponse cancel(Long memberId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.isOwnedBy(memberId)) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_ACCESS_DENIED);
        }
        return paymentRefundUseCase.refund(payment, payment.refundableAmount(),
                "CANCEL-" + payment.getId(), PaymentRefundReason.ORDER_CANCEL, "구매 취소");
    }
}