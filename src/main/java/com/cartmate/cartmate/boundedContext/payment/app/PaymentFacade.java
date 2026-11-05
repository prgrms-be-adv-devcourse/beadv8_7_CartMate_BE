package com.cartmate.cartmate.boundedContext.payment.app;

import com.cartmate.cartmate.boundedContext.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentDailySummary;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentResponse;
import com.cartmate.cartmate.boundedContext.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentRepository;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentWalletClient;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

// payment 모듈의 정문
@Service
@RequiredArgsConstructor
public class PaymentFacade {
    private final PaymentRepository paymentRepository;
    private final PaymentWalletClient paymentWalletClient;
    private final PaymentChargeUseCase paymentChargeUseCase;
    private final PaymentRefundUseCase paymentRefundUseCase;

    // ===== 결제 (주문이 부른다) =====
    // 주문 결제 트랜잭션에 참여한다. 서버를 나누면 이 메서드가 결제 서버의 내부 API가 된다.
    @Transactional
    public PaymentResult charge(PaymentOrderType orderType, Long orderId, Long memberId, long amount) {
        return toResult(paymentChargeUseCase.charge(toPaymentType(orderType), orderId, memberId, amount));
    }

    // 응답을 못 받았을 때 주문이 결제 여부를 확인하는 조회
    @Transactional(readOnly = true)
    public Optional<PaymentResult> findPayment(PaymentOrderType orderType, Long orderId, Long memberId) {
        return paymentRepository.findByTypeAndOrderIdAndMemberId(toPaymentType(orderType), orderId, memberId)
                .map(PaymentFacade::toResult);
    }

    // 차감 후 주문 반영이 실패했을 때 주문이 요청하는 되돌리기
    @Transactional
    public void compensateCharge(PaymentOrderType orderType, Long orderId, Long memberId) {
        paymentRefundUseCase.compensateCharge(toPaymentType(orderType), orderId, memberId);
    }

    @Transactional(readOnly = true)
    public long getWalletBalance(Long memberId) {
        return paymentWalletClient.getBalance(memberId);
    }

    private static PaymentType toPaymentType(PaymentOrderType orderType) {
        return PaymentType.valueOf(orderType.name());
    }

    private static PaymentResult toResult(Payment p) {
        return new PaymentResult(p.getId(), p.getOrderId(), p.getAmount(), p.getRefundedAmount(),
                p.refundableAmount(), p.getType().name(), p.getStatus().name(), p.getPaidAt(), p.getCanceledAt());
    }

    // ===== 결제 조회 =====
    @Transactional(readOnly = true)
    public PaymentResponse getByOrder(Long memberId, Long orderId) {
        return paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.INDIVIDUAL_GROUP, orderId, memberId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getBySharedGroupOrder(Long memberId, Long sharedGroupOrderId) {
        return paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.SHARED_GROUP, sharedGroupOrderId, memberId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    // ===== 환불 =====
// 주문 취소 트랜잭션에 참여한다 (이벤트 리스너에서 호출)
    // 판매자 승인 트랜잭션에 참여한다 (배송 이벤트 리스너에서 호출)
    @Transactional
    public void refundByClaim(Long claimId, Long orderId, Long memberId, Long amount) {
        paymentRefundUseCase.refundByClaim(claimId, orderId, memberId, amount);
    }

    // 주문 취소 트랜잭션에 참여한다 (이벤트 리스너에서 호출)
    @Transactional
    public void refundByBuyerCancel(Long orderId, Long memberId) {
        paymentRefundUseCase.refundByBuyerCancel(orderId, memberId);
    }

    @Transactional
    public int refundCanceledSharedGroupOrder(Long sharedGroupOrderId, String cancelReason) {
        return paymentRefundUseCase.refundCanceledSharedGroupOrder(sharedGroupOrderId, cancelReason);
    }

    @Transactional
    public PaymentResponse refundByAdmin(Long adminId, Long paymentId, Long amount, String memo) {
        return paymentRefundUseCase.refundByAdmin(adminId, paymentId, amount, memo);
    }

    @Transactional(readOnly = true)
    public PaymentDailySummary getDailySummary(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        return paymentRepository.summarize(from, to);
    }

    // 판매글 판매 중지: 개인 주문 판매자 항목 취소 금액만 환불
    @Transactional
    public void refundOrderSellerItems(Long orderId, Long memberId, Long sellerId, long amount) {
        paymentRefundUseCase.refundOrderSellerItems(orderId, memberId, sellerId, amount);
    }

    // 판매글 판매 중지: 공동 주문 판매자 항목 취소 금액을 결제한 사람들에게 나눠 환불
    @Transactional
    public void refundSharedSellerItems(Long sharedGroupOrderId, Long sellerId, long amount) {
        paymentRefundUseCase.refundSharedSellerItems(sharedGroupOrderId, sellerId, amount);
    }
}