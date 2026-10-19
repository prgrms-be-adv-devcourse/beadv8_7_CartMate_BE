package com.cartmate.cartmate.boundedContext.payment.app;

import com.cartmate.cartmate.boundedContext.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentRefundReason;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentStatus;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentSharedGroupRefundUseCase {
    private final PaymentRepository paymentRepository;
    private final PaymentRefundUseCase paymentRefundUseCase;

    // 공동 주문이 확정 전에 취소되면 이미 낸 분담금을 전부 환불
    public int refundAll(Long sharedGroupOrderId, String cancelReason) {
        List<Payment> paid = paymentRepository.findAllByTypeAndOrderIdAndStatus(
                PaymentType.GROUP_SHARE, sharedGroupOrderId, PaymentStatus.PAID);

        for (Payment payment : paid) {
            paymentRefundUseCase.refund(payment, payment.refundableAmount(),
                    "GROUP-CANCEL-" + payment.getId(), PaymentRefundReason.ORDER_CANCEL,
                    "공동물품 구매 취소: " + cancelReason);
        }
        return paid.size();
    }
}