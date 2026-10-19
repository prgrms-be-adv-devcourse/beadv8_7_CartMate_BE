package com.cartmate.cartmate.boundedContext.payment.out;

import com.cartmate.cartmate.boundedContext.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.shared.order.dto.SharedGroupOrderPaymentInfo;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// order 모듈의 공동 주문과 닿는 유일한 곳
@Component
@RequiredArgsConstructor
public class SharedGroupOrderFacadeClient implements PaymentSharedGroupOrderClient {
    private final SharedGroupOrderFacade sharedGroupOrderFacade;

    @Override
    public SharedGroupOrderPaymentInfo getPaymentInfo(Long sharedGroupOrderId, Long memberId, LocalDateTime now) {
        return sharedGroupOrderFacade.getPaymentInfo(sharedGroupOrderId, memberId, now);
    }

    @Override
    public void markStockReserved(Long sharedGroupOrderId) {
        sharedGroupOrderFacade.markStockReserved(sharedGroupOrderId);
    }

    @Override
    public void applyPayment(Long sharedGroupOrderId, Long memberId, Long paymentId, LocalDateTime paidAt,
                             TeamDeliveryAddress address) {
        sharedGroupOrderFacade.applyPayment(sharedGroupOrderId, memberId, paymentId, paidAt, address);
    }
}