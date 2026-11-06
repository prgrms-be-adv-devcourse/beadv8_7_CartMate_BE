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

/**
 * 결제 서버 대역. 주문 서버가 분리돼 테스트에는 결제 서버가 없다(MSA-3).
 * 실제 결제 서버와 같은 약속만 지킨다: (구분 · 주문 · 회원) 키로 한 번만 차감하고, 같은 키로 다시 오면 기존 결제를 돌려준다.
 * 잔액은 충분하다고 본다 — 잔액 부족 같은 결제 서버 쪽 규칙은 결제 서버 테스트가 맡는다.
 */
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
