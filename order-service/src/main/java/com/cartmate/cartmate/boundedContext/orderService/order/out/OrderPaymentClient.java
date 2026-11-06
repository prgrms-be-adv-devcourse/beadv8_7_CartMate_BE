package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;

import java.util.Optional;

public interface OrderPaymentClient {

    PaymentResult charge(PaymentOrderType orderType, Long orderId, Long memberId, long amount);

    Optional<PaymentResult> findPayment(PaymentOrderType orderType, Long orderId, Long memberId);

    long getWalletBalance(Long memberId);

    void compensateCharge(PaymentOrderType orderType, Long orderId, Long memberId);
}
