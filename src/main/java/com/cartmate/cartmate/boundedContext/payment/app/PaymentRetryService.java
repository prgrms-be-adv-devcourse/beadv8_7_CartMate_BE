package com.cartmate.cartmate.boundedContext.payment.app;

import com.cartmate.cartmate.boundedContext.payment.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

// 지갑 낙관적 락 충돌(같은 회원이 동시에 결제 등) 시 트랜잭션 전체를 다시 시도한다.
// 결제는 (주문, 회원) 기준으로 멱등이라 재시도해도 두 번 빠지지 않는다.
@Service
@RequiredArgsConstructor
public class PaymentRetryService {
    private final PaymentFacade paymentFacade;

    @Retryable(
            value = {OptimisticLockingFailureException.class, CannotAcquireLockException.class},
            maxRetries = 3, delay = 100, multiplier = 2, jitter = 50, maxDelay = 1000
    )
    public PaymentResponse payOrder(Long memberId, Long orderId) {
        return paymentFacade.payOrder(memberId, orderId);
    }

    @Retryable(
            value = {OptimisticLockingFailureException.class, CannotAcquireLockException.class},
            maxRetries = 3, delay = 100, multiplier = 2, jitter = 50, maxDelay = 1000
    )
    public PaymentResponse cancel(Long memberId, Long paymentId) {
        return paymentFacade.cancel(memberId, paymentId);
    }
}