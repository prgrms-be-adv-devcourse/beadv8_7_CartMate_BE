package com.cartmate.cartmate.boundedContext.payment.app;

import com.cartmate.cartmate.boundedContext.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentStatus;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentResponse;
import com.cartmate.cartmate.boundedContext.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentRepository;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentSharedGroupOrderClient;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentWalletClient;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.dto.SharedGroupOrderPaymentInfo;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

// 공동물품 구매 분담금 결제
@Service
@RequiredArgsConstructor
public class PaymentSharedGroupPayUseCase {
    private final PaymentRepository paymentRepository;
    private final PaymentSharedGroupOrderClient sharedGroupOrderClient;
    private final PaymentWalletClient paymentWalletClient;

    public PaymentResponse pay(Long memberId, Long sharedGroupOrderId, TeamDeliveryAddress teamDeliveryAddress) {
        // 1. 이미 낸 분담금이면 기존 결과 반환
        var existing = paymentRepository.findByTypeAndOrderIdAndMemberId(
                PaymentType.GROUP_SHARE, sharedGroupOrderId, memberId);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (payment.getStatus() == PaymentStatus.PAID) {
                return PaymentResponse.from(payment);
            }
            throw new ServiceException(PaymentErrorCode.PAYMENT_ALREADY_CANCELED);
        }

        LocalDateTime now = LocalDateTime.now();

        // 2. 내 분담금과 결제 가능 여부 (참여자가 아니면 order가 예외)
        SharedGroupOrderPaymentInfo info = sharedGroupOrderClient.getPaymentInfo(sharedGroupOrderId, memberId, now);
        if (!info.payable()) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        }

        // 3. 첫 결제자가 재고 확보를 표시
        // TODO(product): 상품 모듈 재고 선점 API가 나오면 여기서 먼저 재고를 확보한 뒤 표시
        if (info.needsStockReservation()) {
            sharedGroupOrderClient.markStockReserved(sharedGroupOrderId);
        }

        Long walletId = paymentWalletClient.getWalletId(memberId);

        // 4. 결제 기록 → 예치금 차감 → order에 반영. 하나라도 실패하면 전부 롤백
        Payment payment;
        try {
            payment = paymentRepository.saveAndFlush(
                    Payment.pay(sharedGroupOrderId, memberId, walletId, info.shareAmount(), PaymentType.GROUP_SHARE));
        } catch (DataIntegrityViolationException e) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_IN_PROGRESS);
        }

        paymentWalletClient.use(walletId, payment.getAmount(), payment.getId(), sharedGroupOrderId);

        // 개설자가 팀 배송지를 안 보냈으면 order가 예외 → 차감도 롤백
        // 마지막 참여자의 결제면 order가 주문을 확정하고 확정 이벤트를 발행
        sharedGroupOrderClient.applyPayment(sharedGroupOrderId, memberId, payment.getId(),
                payment.getPaidAt(), teamDeliveryAddress);

        return PaymentResponse.from(payment);
    }
}