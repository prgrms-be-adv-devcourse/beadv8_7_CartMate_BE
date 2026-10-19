package com.cartmate.cartmate.boundedContext.payment.app;

import com.cartmate.cartmate.boundedContext.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentStatus;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentResponse;
import com.cartmate.cartmate.boundedContext.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentOrderClient;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentOrderInfo;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentRepository;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentWalletClient;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.payment.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentPayUseCase {
    private final PaymentRepository paymentRepository;
    private final PaymentOrderClient paymentOrderClient;
    private final PaymentWalletClient paymentWalletClient;
    private final EventPublisher eventPublisher;

    public PaymentResponse payOrder(Long memberId, Long orderId) {
        // 1. 이미 결제한 주문이면 새로 결제하지 않고 기존 결과를 돌려준다 (버튼 두 번, 재시도 대비)
        var existing = paymentRepository.findByOrderIdAndMemberId(orderId, memberId);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (payment.getStatus() == PaymentStatus.PAID) {
                return PaymentResponse.from(payment);
            }
            throw new ServiceException(PaymentErrorCode.PAYMENT_ALREADY_CANCELED);
        }

        // 2. 주문 확인: 금액은 주문 모듈이 계산한 값만 사용
        PaymentOrderInfo order = paymentOrderClient.findOrder(orderId)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND));
        if (!order.memberId().equals(memberId)) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_ORDER_ACCESS_DENIED);
        }
        if (!order.payable()) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        }

        Long walletId = paymentWalletClient.getWalletId(memberId);

        // 3. 결제 기록을 먼저 저장해서 paymentId를 받는다 (wallet.use에 필요)
        Payment payment;
        try {
            payment = paymentRepository.saveAndFlush(
                    Payment.pay(orderId, memberId, walletId, order.amount(), PaymentType.INDIVIDUAL));
        } catch (DataIntegrityViolationException e) {
            // 같은 주문 결제 요청이 동시에 들어온 경우
            throw new ServiceException(PaymentErrorCode.PAYMENT_IN_PROGRESS);
        }

        // 4. 예치금 차감. 잔액 부족이면 예외 → 트랜잭션 전체 롤백 (결제 기록도 사라짐)
        paymentWalletClient.use(walletId, payment.getAmount(), payment.getId(), orderId);

        // 5. 결과 알림. order가 받아서 주문 상태를 바꾼다
        eventPublisher.publish(new PaymentCompletedEvent(
                payment.getId(), orderId, memberId, payment.getAmount(), payment.getPaidAt()));

        return PaymentResponse.from(payment);
    }
}