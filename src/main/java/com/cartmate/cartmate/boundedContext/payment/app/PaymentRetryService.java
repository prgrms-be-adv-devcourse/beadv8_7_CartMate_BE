package com.cartmate.cartmate.boundedContext.payment.app;

import com.cartmate.cartmate.boundedContext.payment.dto.PaymentResponse;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

// 지갑·공동 주문의 락 충돌 시 트랜잭션 전체를 다시 시도한다.
// 결제는 (종류, 주문, 회원) 기준으로 멱등이라 재시도해도 두 번 빠지지 않는다.
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
    public PaymentResponse paySharedGroupOrder(Long memberId, Long sharedGroupOrderId, TeamDeliveryAddress address) {
        return paymentFacade.paySharedGroupOrder(memberId, sharedGroupOrderId, address);
    }
}