package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Primary
@Profile("test")
public class FakeOrderPaymentClient implements OrderPaymentClient {

    private static final long BALANCE = 1_000_000_000L;

    private final Map<String, PaymentResult> payments = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public PaymentResult charge(PaymentOrderType orderType, Long orderId, Long memberId, long amount) {
        return payments.computeIfAbsent(key(orderType, orderId, memberId), k -> new PaymentResult(
                sequence.incrementAndGet(), orderId, amount, 0L, amount, orderType.name(), "PAID",
                LocalDateTime.now(), null));
    }

    @Override
    public Optional<PaymentResult> findPayment(PaymentOrderType orderType, Long orderId, Long memberId) {
        return Optional.ofNullable(payments.get(key(orderType, orderId, memberId)));
    }

    @Override
    public long getWalletBalance(Long memberId) {
        return BALANCE;
    }

    @Override
    public void compensateCharge(PaymentOrderType orderType, Long orderId, Long memberId) {
        payments.remove(key(orderType, orderId, memberId));
    }

    private static String key(PaymentOrderType orderType, Long orderId, Long memberId) {
        return orderType + ":" + orderId + ":" + memberId;
    }
}
