package com.cartmate.cartmate.boundedContext.orderService.order.app;

import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
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
        try {
            return orderFacade.payOrder(memberId, orderId, memberCouponId);
        } catch (ServiceException e) {
            // 재고 부족: 결제 트랜잭션은 롤백됐으니 주문 취소는 따로(새 트랜잭션) 한다. 결제 전이라 돈은 나가지 않았다
            if (OrderErrorCode.ORDER_OUT_OF_STOCK.resultCode().equals(e.getRsData().getResultCode())) {
                orderFacade.cancelByOutOfStock(orderId);
            }
            throw e;
        }
    }

    @Retryable(
            value = {OptimisticLockingFailureException.class, CannotAcquireLockException.class},
            maxRetries = 3, delay = 100, multiplier = 2, jitter = 50, maxDelay = 1000
    )
    public PaymentResult paySharedGroupOrder(Long memberId, Long sharedGroupOrderId, TeamDeliveryAddress address) {
        return sharedGroupOrderFacade.pay(memberId, sharedGroupOrderId, address);
    }
}