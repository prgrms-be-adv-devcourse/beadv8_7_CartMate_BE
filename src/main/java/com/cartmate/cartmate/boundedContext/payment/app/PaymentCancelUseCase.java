package com.cartmate.cartmate.boundedContext.payment.app;

import com.cartmate.cartmate.boundedContext.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentResponse;
import com.cartmate.cartmate.boundedContext.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentRepository;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentWalletClient;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.payment.event.PaymentCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentCancelUseCase {
    private final PaymentRepository paymentRepository;
    private final PaymentWalletClient paymentWalletClient;
    private final EventPublisher eventPublisher;

    // 전액 취소. 부분 취소(일부 상품만)는 주문 취소 정책이 정해지면 추가
    public PaymentResponse cancel(Long memberId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.isOwnedBy(memberId)) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_ACCESS_DENIED);
        }

        payment.cancel(); // 이미 취소면 예외

        // 전액 환불이라 refundId로 paymentId를 사용 (wallet이 refundId 기준으로 중복 환불을 막음)
        paymentWalletClient.refund(payment.getWalletId(), payment.getAmount(),
                payment.getId(), payment.getOrderId(), payment.getId());

        eventPublisher.publish(new PaymentCanceledEvent(
                payment.getId(), payment.getOrderId(), memberId, payment.getAmount(), payment.getCanceledAt()));

        return PaymentResponse.from(payment);
    }
}