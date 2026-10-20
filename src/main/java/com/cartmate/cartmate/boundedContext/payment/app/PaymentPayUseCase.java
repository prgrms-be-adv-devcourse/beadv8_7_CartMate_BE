package com.cartmate.cartmate.boundedContext.payment.app;

import com.cartmate.cartmate.boundedContext.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentStatus;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentResponse;
import com.cartmate.cartmate.boundedContext.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentOrderClient;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentRepository;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentWalletClient;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.dto.OrderPaymentInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

// 개인 주문 결제 (일반 주문 + 개인물품 공동구매 주문)
@Service
@RequiredArgsConstructor
public class PaymentPayUseCase {
    private final PaymentRepository paymentRepository;
    private final PaymentOrderClient paymentOrderClient;
    private final PaymentWalletClient paymentWalletClient;

    public PaymentResponse payOrder(Long memberId, Long orderId) {
        // 1. 이미 결제한 주문이면 기존 결과 반환
        var existing = paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.INDIVIDUAL, orderId, memberId);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (payment.getStatus() == PaymentStatus.PAID) {
                return PaymentResponse.from(payment);
            }
            throw new ServiceException(PaymentErrorCode.PAYMENT_ALREADY_CANCELED);
        }

        // 2. 주문 확인 (구매자가 아니면 order가 예외). 금액은 order가 계산한 값만 사용
        OrderPaymentInfo order = paymentOrderClient.getPaymentInfo(orderId, memberId, LocalDateTime.now());
        if (!order.payable()) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        }

        Long walletId = paymentWalletClient.getWalletId(memberId);

        // 3. 결제 기록 → 예치금 차감 → order에 반영. 하나라도 실패하면 전부 롤백
        Payment payment;
        try {
            payment = paymentRepository.saveAndFlush(
                    Payment.pay(orderId, memberId, walletId, order.amount(), PaymentType.INDIVIDUAL));
        } catch (DataIntegrityViolationException e) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_IN_PROGRESS);
        }

        paymentWalletClient.use(walletId, payment.getAmount(), payment.getId(), orderId, PaymentType.INDIVIDUAL);

        // order가 주문을 결제 완료로 바꾸고 OrderPaidEvent를 발행 (장바구니 비우기 등은 그쪽에서)
        paymentOrderClient.applyPayment(orderId, memberId, payment.getId(), payment.getPaidAt());

        return PaymentResponse.from(payment);
    }
}