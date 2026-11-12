package com.cartmate.cartmate.boundedContext.paymentService.payment.domain;

import com.cartmate.cartmate.boundedContext.paymentService.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedRefundSplitTest {

    private static Payment payment(long id, long amount) {
        Payment payment = Payment.pay(100L, id, id, amount, PaymentType.SHARED_GROUP);
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }

    @Test
    @DisplayName("분담금이 같으면 n분의 1로 나누고, 끝전은 결제 ID 순으로 1원씩 붙인다")
    void splitsEquallyWithRemainder() {
        List<Payment> payments = List.of(payment(3L, 10_000), payment(1L, 10_000), payment(2L, 10_000));

        Map<Long, Long> parts = SharedRefundSplit.split(payments, 10_001);

        assertThat(parts).containsExactly(Map.entry(1L, 3_334L), Map.entry(2L, 3_334L), Map.entry(3L, 3_333L));
    }

    @Test
    @DisplayName("분담금 끝전을 낸 만든 사람이 끝전 1원을 먼저 받는다")
    void remainderGoesToLargestPayerFirst() {
        List<Payment> payments = List.of(payment(1L, 10_000), payment(2L, 10_000), payment(3L, 10_001));

        Map<Long, Long> parts = SharedRefundSplit.split(payments, 10_001);

        assertThat(parts).containsExactly(Map.entry(3L, 3_334L), Map.entry(1L, 3_334L), Map.entry(2L, 3_333L));
        assertThat(parts.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(10_001);
    }

    @Test
    @DisplayName("낸 금액이 다르면 비율대로 나눈다")
    void splitsByPaidRatio() {
        List<Payment> payments = List.of(payment(1L, 20_000), payment(2L, 10_000));

        Map<Long, Long> parts = SharedRefundSplit.split(payments, 9_000);

        assertThat(parts).containsExactly(Map.entry(1L, 6_000L), Map.entry(2L, 3_000L));
    }

    @Test
    @DisplayName("이미 환불된 결제도 원래 결제 금액으로 똑같이 나눈다")
    void ignoresRefundState() {
        Payment refunded = payment(1L, 10_000);
        refunded.refund(10_000L);
        List<Payment> payments = List.of(refunded, payment(2L, 10_000), payment(3L, 10_000));

        Map<Long, Long> parts = SharedRefundSplit.split(payments, 10_001);

        assertThat(parts).containsExactly(Map.entry(1L, 3_334L), Map.entry(2L, 3_334L), Map.entry(3L, 3_333L));
    }

    @Test
    @DisplayName("나눌 금액이 낸 금액 합보다 크거나 0 이하면 예외")
    void rejectsInvalidAmount() {
        List<Payment> payments = List.of(payment(1L, 10_000), payment(2L, 10_000));

        assertThatThrownBy(() -> SharedRefundSplit.split(payments, 20_001))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(PaymentErrorCode.PAYMENT_REFUND_EXCEEDS_REMAINING.getMsg());
        assertThatThrownBy(() -> SharedRefundSplit.split(payments, 0))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> SharedRefundSplit.split(List.of(), 1_000))
                .isInstanceOf(ServiceException.class);
    }
}
