package com.cartmate.cartmate.boundedContext.paymentService.payment.app;

import com.cartmate.cartmate.boundedContext.paymentService.payment.domain.*;
import com.cartmate.cartmate.boundedContext.paymentService.payment.dto.PaymentResponse;
import com.cartmate.cartmate.boundedContext.paymentService.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.boundedContext.paymentService.payment.out.PaymentRefundRepository;
import com.cartmate.cartmate.boundedContext.paymentService.payment.out.PaymentRepository;
import com.cartmate.cartmate.boundedContext.paymentService.payment.out.PaymentWalletClient;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        refund(payment, amount, RefundKey.claim(claimId),
                PaymentRefundReason.CLAIM_REFUND, "판매자 승인 환불 (클레임 #" + claimId + ")");
    }

    // 주문 취소 → order가 정해 보낸 금액을 환불한다 (결제 금액 − 판매글 내림으로 인한 취소로 이미 환불한 금액).
    // 남은 금액 전부를 쓰면 판매글 내림 환불 이벤트보다 먼저 처리됐을 때 그 몫까지 돌려주고, 뒤에 온 이벤트가 실패한다.
    // 금액이 없는 옛 이벤트는 남은 금액 전부를 환불한다.
    public void refundByBuyerCancel(Long orderId, Long memberId, Long refundAmount) {
        Payment payment = paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.INDIVIDUAL_GROUP, orderId, memberId)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        long amount = refundAmount != null ? refundAmount : payment.refundableAmount();
        if (amount == 0) {
            log.info("주문 #{} 취소: 환불할 금액이 남아 있지 않음", orderId);
            return;
        }
        validateRefundAmount(amount);
        refund(payment, amount, RefundKey.orderCancel(payment.getId()),
                PaymentRefundReason.BUYER_CANCEL, "구매자 주문 취소 (주문 #" + orderId + ")");
    }

    // 공동물품 무산·취소 → 사람마다 "낸 금액 − 먼저 취소된 판매자 항목 금액들을 같은 규칙으로 나눈 내 참여자 몫의 합"을 환불.
    // 남은 금액 전부를 쓰지 않는 이유: 판매자 항목 취소 환불과 전체 취소 환불은 다른 토픽으로 와서 순서를 모른다.
    // 전체 취소가 먼저 처리되면 남은 금액 전부에 판매자 항목 몫까지 들어가고, 뒤에 온 판매자 항목 취소 환불이 실패한다.
    // 이렇게 빼고 환불하면 어느 쪽이 먼저 오든 사람마다 환불 합이 낸 금액과 같아진다.
    // canceledSellerAmounts가 없으면 먼저 취소된 판매자 항목이 없는 것으로 본다.
    public int refundCanceledSharedGroupOrder(Long sharedGroupOrderId, String cancelReason, List<Long> canceledSellerAmounts) {
        List<Payment> payments = sharedSplitTargets(sharedGroupOrderId);
        Map<Long, Long> alreadyCanceled = new HashMap<>();
        for (long sellerAmount : canceledSellerAmounts == null ? List.<Long>of() : canceledSellerAmounts) {
            SharedRefundSplit.split(payments, sellerAmount)
                    .forEach((paymentId, part) -> alreadyCanceled.merge(paymentId, part, Long::sum));
        }

        int refunded = 0;
        for (Payment payment : payments) {
            long amount = payment.getAmount() - alreadyCanceled.getOrDefault(payment.getId(), 0L);
            if (amount < 0) {
                throw new ServiceException(PaymentErrorCode.PAYMENT_REFUND_EXCEEDS_REMAINING);
            }
            if (amount == 0) {
                continue;
            }
            refund(payment, amount, RefundKey.sharedGroupCancel(payment.getId()),
                    PaymentRefundReason.SHARED_GROUP_CANCEL, "공동물품 구매 취소: " + cancelReason);
            refunded++;
        }
        log.info("공동 주문 #{} 취소로 {}건 환불", sharedGroupOrderId, refunded);
        return refunded;
    }

    // 개인 주문에서 한 판매자 항목만 취소됨(판매 중지 또는 판매자 사유) → 그 금액만 환불.
    // 환불 키가 주문·판매자라서 같은 판매자 항목이 판매 중지와 판매자 사유로 두 번 와도 한 번만 환불된다.
    public void refundOrderSellerItems(Long orderId, Long memberId, Long sellerId, long amount,
                                       DeliveryCancelReason cancelReason) {
        validateRefundAmount(amount);
        Payment payment = paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.INDIVIDUAL_GROUP, orderId, memberId)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        refund(payment, amount, RefundKey.orderSellerItems(orderId, sellerId),
                sellerItemsRefundReason(cancelReason), sellerItemsMemo(cancelReason, ""));
    }

    // 확정 공동 주문에서 한 판매자 항목만 취소됨 → 결제한 사람들에게 참여자 몫으로 나눠 환불 (나누는 규칙은 SharedRefundSplit).
    // 확정된 공동 주문인데 결제가 없다면 데이터가 어긋난 것이므로 조용히 넘기지 않고 예외로 주문 취소까지 롤백한다.
    public void refundSharedSellerItems(Long sharedGroupOrderId, Long sellerId, long amount,
                                        DeliveryCancelReason cancelReason) {
        validateRefundAmount(amount);
        List<Payment> payments = sharedSplitTargets(sharedGroupOrderId);
        Map<Long, Long> parts = SharedRefundSplit.split(payments, amount);

        for (Payment payment : payments) {
            long part = parts.get(payment.getId());
            if (part > 0) {
                refund(payment, part, RefundKey.sharedSellerItems(sharedGroupOrderId, sellerId, payment.getId()),
                        sellerItemsRefundReason(cancelReason), sellerItemsMemo(cancelReason, "참여자 몫 "));
            }
        }
        log.info("공동 주문 #{} 판매자 #{} 취소 상품 {}원을 {}명에게 나눠 환불", sharedGroupOrderId, sellerId, amount, payments.size());
    }

    // 공동 주문 환불을 참여자 몫으로 나눌 대상: 그 공동 주문의 결제 전부(이미 환불된 결제 포함).
    // 상태로 거르면 한쪽 환불이 먼저 끝났을 때 대상이 달라져 두 환불이 다르게 나눈다.
    // 결제 직후 주문 반영 실패로 되돌린 결제는 참여가 성립하지 않은 것이라 뺀다.
    private List<Payment> sharedSplitTargets(Long sharedGroupOrderId) {
        return paymentRepository.findAllByTypeAndOrderId(PaymentType.SHARED_GROUP, sharedGroupOrderId).stream()
                .filter(payment -> !paymentRefundRepository.existsByRefundKey(RefundKey.chargeCompensation(payment.getId())))
                .toList();
    }

    // 사유가 없으면 판매 중지로 본다
    private PaymentRefundReason sellerItemsRefundReason(DeliveryCancelReason cancelReason) {
        return isSellerReason(cancelReason) ? PaymentRefundReason.SELLER_CANCEL : PaymentRefundReason.PRODUCT_HIDDEN;
    }

    private String sellerItemsMemo(DeliveryCancelReason cancelReason, String share) {
        return isSellerReason(cancelReason)
                ? "판매자 사유 취소 %s자동 환불(%s)".formatted(share, cancelReason.getLabel())
                : "판매 중지 상품 %s자동 환불".formatted(share);
    }

    private boolean isSellerReason(DeliveryCancelReason cancelReason) {
        return cancelReason != null && cancelReason.isSellerReason();
    }

// 이벤트로 받은 환불 금액이 0 이하면 앞 단계 계산이 틀어진 것 → 무시하지 않고 예외
    private void validateRefundAmount(long amount) {
        if (amount <= 0) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_REFUND_INVALID_AMOUNT);
        }
    }

    

    // 관리자 수동 환불 (부분 가능)
    public PaymentResponse refundByAdmin(Long adminId, Long paymentId, Long amount, String memo) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        String key = RefundKey.admin(paymentId, System.currentTimeMillis());
        refund(payment, amount, key, PaymentRefundReason.ADMIN,
                "관리자(%d) %s".formatted(adminId, memo == null ? "" : memo));
        return PaymentResponse.from(payment);
    }

    // 차감 후 주문 반영이 실패했을 때 되돌린다 (서버가 나뉘어 차감과 주문 반영이 다른 트랜잭션일 때).
    // 결제가 없거나 이미 되돌렸으면 아무것도 안 한다 (주문이 같은 보상을 여러 번 요청해도 안전).
    public void compensateCharge(PaymentType type, Long orderId, Long memberId) {
        paymentRepository.findByTypeAndOrderIdAndMemberId(type, orderId, memberId)
                .filter(payment -> payment.refundableAmount() > 0)
                .ifPresent(payment -> refund(payment, payment.refundableAmount(),
                        RefundKey.chargeCompensation(payment.getId()), PaymentRefundReason.CHARGE_COMPENSATION,
                        "주문 반영 실패로 결제 자동 취소 (주문 #" + orderId + ")"));
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