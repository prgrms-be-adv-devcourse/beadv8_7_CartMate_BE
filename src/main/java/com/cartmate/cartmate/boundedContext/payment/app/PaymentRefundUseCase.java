package com.cartmate.cartmate.boundedContext.payment.app;

import com.cartmate.cartmate.boundedContext.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentRefund;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentRefundReason;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentStatus;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentResponse;
import com.cartmate.cartmate.boundedContext.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentRefundRepository;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentRepository;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentWalletClient;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

// 모든 환불이 지나가는 곳
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundUseCase {
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final PaymentWalletClient paymentWalletClient;

    // 판매자가 승인한 취소·반품 요청 → 승인된 금액만 환불
    public void refundByClaim(Long claimId, Long orderId, Long memberId, Long amount) {
        Payment payment = paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.INDIVIDUAL, orderId, memberId)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        refund(payment, amount, "CLAIM-" + claimId,
                PaymentRefundReason.CLAIM_REFUND, "판매자 승인 환불 (클레임 #" + claimId + ")");
    }

    // 공동물품 무산 → 이미 낸 분담금 전원 환불
    public int refundCanceledSharedGroupOrder(Long sharedGroupOrderId, String cancelReason) {
        List<Payment> payments = paymentRepository.findAllByTypeAndOrderIdAndStatusIn(
                PaymentType.GROUP_SHARE, sharedGroupOrderId,
                List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED));

        for (Payment payment : payments) {
            refund(payment, payment.refundableAmount(), "SHARED-CANCEL-" + payment.getId(),
                    PaymentRefundReason.SHARED_GROUP_CANCEL, "공동물품 구매 취소: " + cancelReason);
        }
        log.info("공동 주문 #{} 취소로 {}건 환불", sharedGroupOrderId, payments.size());
        return payments.size();
    }

    // 관리자 수동 환불 (부분 가능)
    public PaymentResponse refundByAdmin(Long adminId, Long paymentId, Long amount, String memo) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        String key = "ADMIN-%d-%d".formatted(paymentId, System.currentTimeMillis());
        refund(payment, amount, key, PaymentRefundReason.ADMIN,
                "관리자(%d) %s".formatted(adminId, memo == null ? "" : memo));
        return PaymentResponse.from(payment);
    }

    private void refund(Payment payment, Long amount, String refundKey, PaymentRefundReason reason, String memo) {
        // 같은 원인으로 이미 환불했으면 다시 하지 않음 (이벤트 중복, 재시도 대비)
        if (paymentRefundRepository.existsByRefundKey(refundKey)) {
            return;
        }

        payment.refund(amount); // 남은 금액 초과, 이미 전액 환불이면 예외

        PaymentRefund refund;
        try {
            refund = paymentRefundRepository.saveAndFlush(
                    PaymentRefund.create(payment.getId(), refundKey, amount, reason, memo));
        } catch (DataIntegrityViolationException e) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_REFUND_IN_PROGRESS);
        }

        // 지갑 환불. 환불 기록 ID를 refundId로 넘겨 wallet도 중복 환불을 막는다.
        // 이미 정산된 결제면 wallet이 거절 → 주문 취소까지 롤백
        paymentWalletClient.refund(payment.getWalletId(), amount,
                payment.getId(), payment.getOrderId(), refund.getId());
    }
}