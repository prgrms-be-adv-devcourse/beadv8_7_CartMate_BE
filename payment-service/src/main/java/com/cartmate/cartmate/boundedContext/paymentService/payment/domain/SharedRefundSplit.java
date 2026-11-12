package com.cartmate.cartmate.boundedContext.paymentService.payment.domain;

import com.cartmate.cartmate.boundedContext.paymentService.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 공동 주문에서 취소된 판매자 항목 금액을 결제한 사람들의 참여자 몫으로 나누는 규칙.
 *
 * <p>판매자 항목 취소 환불과 공동 주문 전체 취소 환불은 어느 쪽이 먼저 처리될지 모른다.
 * 두 곳이 다르게 나누면 한 사람에게 낸 돈보다 많이 돌려주려 하거나 덜 돌려주게 된다.
 * 그래서 대상은 환불 상태와 상관없이 그 공동 주문의 결제 전부로, 비율은 원래 결제 금액으로 정한다.
 */
public final class SharedRefundSplit {

    private static final Comparator<Payment> ORDER =
            Comparator.comparing(Payment::getAmount).reversed().thenComparing(Payment::getId);

    private SharedRefundSplit() {
    }

    // 낸 금액 비율대로 나눈다(같으면 n분의 1). 끝전은 많이 낸 사람(분담금 끝전을 낸 만든 사람)부터, 같으면 결제 ID 순으로 1원씩.
    // 그래야 각자 순 부담이 남은 상품의 n분의 1과 맞는다.
    // 결과: 결제 ID → 참여자 몫 (많이 낸 순서)
    public static Map<Long, Long> split(List<Payment> payments, long amount) {
        if (amount <= 0) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_REFUND_INVALID_AMOUNT);
        }
        long total = payments.stream().mapToLong(Payment::getAmount).sum();
        if (total <= 0) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
        if (amount > total) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_REFUND_EXCEEDS_REMAINING);
        }

        List<Payment> sorted = payments.stream().sorted(ORDER).toList();
        Map<Long, Long> parts = new LinkedHashMap<>();
        long distributed = 0;
        for (Payment payment : sorted) {
            long part = amount * payment.getAmount() / total;
            parts.put(payment.getId(), part);
            distributed += part;
        }
        // 내림으로 버린 끝전은 사람 수보다 적으므로 앞에서부터 한 바퀴 안에 다 붙는다
        for (int i = 0; distributed < amount; i++, distributed++) {
            parts.merge(sorted.get(i).getId(), 1L, Long::sum);
        }
        return parts;
    }
}
