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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
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
        Payment payment = paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.INDIVIDUAL_GROUP, orderId, memberId)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        refund(payment, amount, "CLAIM-" + claimId,
                PaymentRefundReason.CLAIM_REFUND, "판매자 승인 환불 (클레임 #" + claimId + ")");
    }

    // 판매자 확인 전 구매자가 주문을 취소 → 결제 금액 전액 환불
    public void refundByBuyerCancel(Long orderId, Long memberId) {
        Payment payment = paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.INDIVIDUAL_GROUP, orderId, memberId)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        refund(payment, payment.refundableAmount(), "ORDER-CANCEL-" + payment.getId(),
                PaymentRefundReason.BUYER_CANCEL, "구매자 주문 취소 (주문 #" + orderId + ")");
    }

    // 공동물품 무산 → 이미 낸 분담금 전원 환불
    public int refundCanceledSharedGroupOrder(Long sharedGroupOrderId, String cancelReason) {
        List<Payment> payments = paymentRepository.findAllByTypeAndOrderIdAndStatusIn(
                PaymentType.SHARED_GROUP, sharedGroupOrderId,
                List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED));

        for (Payment payment : payments) {
            refund(payment, payment.refundableAmount(), "SHARED-CANCEL-" + payment.getId(),
                    PaymentRefundReason.SHARED_GROUP_CANCEL, "공동물품 구매 취소: " + cancelReason);
        }
        log.info("공동 주문 #{} 취소로 {}건 환불", sharedGroupOrderId, payments.size());
        return payments.size();
    }

    // 판매글 판매 중지: 개인 주문에서 한 판매자 항목만 취소됨 → 그 금액만 환불 (cancelKey로 중복 방지)
    public void refundOrderSellerItems(Long orderId, Long memberId, long amount, String cancelKey) {
        Payment payment = paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.INDIVIDUAL_GROUP, orderId, memberId)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        refund(payment, amount, cancelKey, PaymentRefundReason.PRODUCT_HIDDEN, "판매 중지 상품 자동 환불");
    }

    // 판매글 판매 중지: 확정 공동 주문에서 한 판매자 항목만 취소됨 → 결제한 사람들에게 낸 금액 비율대로 나눠 환불.
    // 분담금이 같으면 n분의 1. 끝전은 많이 낸 사람(분담금 끝전을 낸 만든 사람)부터 1원씩 →
    // 각자 순 부담이 남은 상품의 n분의 1과 맞는다.
    public void refundSharedSellerItems(Long sharedGroupOrderId, long amount, String cancelKey) {
        List<Payment> payments = new ArrayList<>(paymentRepository.findAllByTypeAndOrderIdAndStatusIn(
                PaymentType.SHARED_GROUP, sharedGroupOrderId,
                List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED)));
        long total = payments.stream().mapToLong(Payment::getAmount).sum();
        if (total == 0 || amount <= 0) {
            return;
        }
        payments.sort(Comparator.comparing(Payment::getAmount).reversed().thenComparing(Payment::getId));
        long[] parts = new long[payments.size()];
        long distributed = 0;
        for (int i = 0; i < payments.size(); i++) {
            parts[i] = amount * payments.get(i).getAmount() / total;
            distributed += parts[i];
        }
        for (int i = 0; distributed < amount; i = (i + 1) % parts.length) {
            parts[i]++;
            distributed++;
        }

        for (int i = 0; i < payments.size(); i++) {
            if (parts[i] > 0) {
                Payment payment = payments.get(i);
                refund(payment, parts[i], cancelKey + "-PAY" + payment.getId(),
                        PaymentRefundReason.PRODUCT_HIDDEN, "판매 중지 상품 몫 자동 환불");
            }
        }
        log.info("공동 주문 #{} 판매 중지 상품 {}원을 {}명에게 나눠 환불", sharedGroupOrderId, amount, payments.size());
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
            // 같은 환불 키가 동시에 저장된 경우(중복 키)만 "처리 중"으로 바꾼다.
            // CHECK 제약 위반 등 다른 원인은 그대로 던져서, 엉뚱한 메시지 뒤에 숨지 않게 한다.
            if (isDuplicateKey(e)) {
                throw new ServiceException(PaymentErrorCode.PAYMENT_REFUND_IN_PROGRESS);
            }
            throw e;
        }

        // 지갑 환불. 환불 기록 ID를 refundId로 넘겨 wallet도 중복 환불을 막는다.
        // 이미 정산된 결제면 wallet이 거절 → 주문 취소까지 롤백
        paymentWalletClient.refund(payment.getWalletId(), amount,
                payment.getId(), payment.getOrderId(), refund.getId());
    }

    // MySQL 중복 키 오류(1062)인지. 저장 실패 직후라 DB를 다시 조회하지 않고 예외 안의 오류 번호로 판단한다.
    private boolean isDuplicateKey(DataIntegrityViolationException e) {
        return e.getMostSpecificCause() instanceof SQLException sql && sql.getErrorCode() == 1062;
    }
}