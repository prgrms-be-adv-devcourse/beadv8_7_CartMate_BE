package com.cartmate.cartmate.boundedContext.orderService.order.app;

import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * 결제 트랜잭션 전체를 재시도한다 (결제 모듈에 있던 것을 결제를 지휘하는 주문으로 옮김).
 * 예치금 동시 차감 충돌·잠금 대기 초과 시 트랜잭션을 처음부터 다시 실행한다.
 * 차감은 (구분·주문·회원) 키로 한 번만이라 다시 실행해도 두 번 빠지지 않는다.
 */
@Service
@RequiredArgsConstructor
public class OrderPaymentRetryService {
    private final OrderFacade orderFacade;
    private final SharedGroupOrderFacade sharedGroupOrderFacade;

    @Retryable(
            value = {OptimisticLockingFailureException.class, CannotAcquireLockException.class},
            maxRetries = 3, delay = 100, multiplier = 2, jitter = 50, maxDelay = 1000
    )
    public PaymentResult payOrder(Long memberId, Long orderId) {
        return payOrder(memberId, orderId, null);
    }

    @Retryable(
            value = {OptimisticLockingFailureException.class, CannotAcquireLockException.class},
            maxRetries = 3, delay = 100, multiplier = 2, jitter = 50, maxDelay = 1000
    )
    public PaymentResult payOrder(Long memberId, Long orderId, Long memberCouponId) {
        return orderFacade.payOrder(memberId, orderId, memberCouponId);
    }

    @Retryable(
            value = {OptimisticLockingFailureException.class, CannotAcquireLockException.class},
            maxRetries = 3, delay = 100, multiplier = 2, jitter = 50, maxDelay = 1000
    )
    public PaymentResult paySharedGroupOrder(Long memberId, Long sharedGroupOrderId, TeamDeliveryAddress address) {
        return sharedGroupOrderFacade.pay(memberId, sharedGroupOrderId, address);
    }
}