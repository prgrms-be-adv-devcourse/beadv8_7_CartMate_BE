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