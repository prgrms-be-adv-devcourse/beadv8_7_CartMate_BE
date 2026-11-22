package com.cartmate.cartmate.boundedContext.paymentService.payment.app;

import com.cartmate.cartmate.boundedContext.paymentService.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.paymentService.payment.domain.PaymentStatus;
import com.cartmate.cartmate.boundedContext.paymentService.payment.domain.PaymentType;
import com.cartmate.cartmate.boundedContext.paymentService.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.boundedContext.paymentService.payment.out.PaymentRepository;
import com.cartmate.cartmate.boundedContext.paymentService.payment.out.PaymentWalletClient;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 예치금 차감. 결제 서버는 주문 내용(결제 기한, 공동 주문 규칙 등)을 모르고
 * "이 회원에게서 이 금액을, (구분·주문·회원) 키로 한 번만" 뺀다.
 *
 * <p>같은 키로 다시 요청하면 이미 한 결제를 그대로 돌려준다. 그래서 주문이 응답을 못 받아
 * 같은 요청을 다시 보내도 두 번 빠지지 않는다 (DB 유니크 제약이 최종 방어선).
 */
@Slf4j
@Service
@RequiredArgsConstructor
class PaymentChargeUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentWalletClient paymentWalletClient;

    Payment charge(PaymentType type, Long orderId, Long memberId, long amount) {
        var existing = paymentRepository.findByTypeAndOrderIdAndMemberId(type, orderId, memberId);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (payment.getStatus() == PaymentStatus.PAID) {
                logCharge(payment, "DUPLICATE"); // 같은 키로 다시 옴 (재시도 · 응답 유실)
                return payment;
            }
            throw new ServiceException(PaymentErrorCode.PAYMENT_ALREADY_CANCELED);
        }

        Long walletId = paymentWalletClient.getWalletId(memberId);
        Payment payment;
        try {
            payment = paymentRepository.saveAndFlush(Payment.pay(orderId, memberId, walletId, amount, type));
        } catch (DataIntegrityViolationException e) {
            // 같은 키의 결제가 동시에 저장됨 → 다른 요청이 처리 중
            throw new ServiceException(PaymentErrorCode.PAYMENT_IN_PROGRESS);
        }

        paymentWalletClient.use(walletId, payment.getAmount(), payment.getId(), orderId, type);
        logSuccessAfterCommit(payment);
        return payment;
    }

    // 성공 기록은 커밋된 뒤에 남긴다. 커밋 단계에서 같은 지갑 충돌(데드락 · 낙관적 락)로 롤백되면
    // 이미 SUCCESS가 찍혀 로그로 센 결제 수가 실제보다 커졌다 (부하 테스트 S4: 로그 500줄, 실제 결제 54건)
    private static void logSuccessAfterCommit(Payment payment) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            logCharge(payment, "SUCCESS");
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                logCharge(payment, "SUCCESS");
            }
        });
    }

    private static void logCharge(Payment payment, String result) {
        log.atInfo().addKeyValue("event", "payment.charge")
                .addKeyValue("orderId", payment.getOrderId())
                .addKeyValue("memberId", payment.getMemberId())
                .addKeyValue("paymentId", payment.getId())
                .addKeyValue("amount", payment.getAmount())
                .addKeyValue("result", result)
                .log("결제 차감 {}: 주문 #{} 결제 #{} {}원", result, payment.getOrderId(), payment.getId(),
                        payment.getAmount());
    }
}