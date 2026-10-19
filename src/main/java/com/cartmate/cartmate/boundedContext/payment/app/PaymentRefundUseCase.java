package com.cartmate.cartmate.boundedContext.payment.app;

import com.cartmate.cartmate.boundedContext.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentRefund;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentRefundReason;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentResponse;
import com.cartmate.cartmate.boundedContext.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentRefundRepository;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentWalletClient;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.payment.event.PaymentRefundedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentRefundUseCase {
    private final PaymentRefundRepository paymentRefundRepository;
    private final PaymentWalletClient paymentWalletClient;
    private final EventPublisher eventPublisher;

    public PaymentResponse refund(Payment payment, Long amount, String refundKey,
                                  PaymentRefundReason reason, String memo) {
        // 같은 원인으로 이미 환불했으면 다시 하지 않음 (이벤트 중복 수신, 재시도 대비)
        if (paymentRefundRepository.findByRefundKey(refundKey).isPresent()) {
            return PaymentResponse.from(payment);
        }

        payment.refund(amount); // 남은 금액 초과, 이미 전액 취소면 예외

        PaymentRefund refund;
        try {
            refund = paymentRefundRepository.saveAndFlush(
                    PaymentRefund.create(payment.getId(), refundKey, amount, reason, memo));
        } catch (DataIntegrityViolationException e) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_REFUND_IN_PROGRESS);
        }

        // 지갑 환불. refundId로 환불 기록 ID를 넘겨서 wallet도 중복 환불을 막는다
        paymentWalletClient.refund(payment.getWalletId(), amount,
                payment.getId(), payment.getOrderId(), refund.getId());

        eventPublisher.publish(new PaymentRefundedEvent(
                payment.getId(), payment.getOrderId(), payment.getMemberId(), amount,
                payment.getRefundedAmount(), payment.refundableAmount() == 0, LocalDateTime.now()));

        return PaymentResponse.from(payment);
    }
}