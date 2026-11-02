package com.cartmate.cartmate.boundedContext.payment.out;

import com.cartmate.cartmate.boundedContext.payment.dto.PaymentDailySummary;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;

import static com.cartmate.cartmate.boundedContext.payment.domain.QPayment.payment;
import static com.cartmate.cartmate.boundedContext.payment.domain.QPaymentRefund.paymentRefund;

public class PaymentRepositoryImpl implements PaymentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public PaymentRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    // 건수와 합계를 한 조회에서 같이 구한다. 결제와 환불은 테이블이 달라 조회 2번.
    @Override
    public PaymentDailySummary summarize(LocalDateTime from, LocalDateTime to) {
        NumberExpression<Long> paidCount = payment.count();
        NumberExpression<Long> paidAmount = payment.amount.sumLong().coalesce(0L);
        Tuple paid = queryFactory
                .select(paidCount, paidAmount)
                .from(payment)
                .where(payment.paidAt.goe(from), payment.paidAt.lt(to))
                .fetchOne();

        NumberExpression<Long> refundCount = paymentRefund.count();
        NumberExpression<Long> refundAmount = paymentRefund.amount.sumLong().coalesce(0L);
        Tuple refunded = queryFactory
                .select(refundCount, refundAmount)
                .from(paymentRefund)
                .where(paymentRefund.createDate.goe(from), paymentRefund.createDate.lt(to))
                .fetchOne();

        return new PaymentDailySummary(
                orZero(paid, paidCount), orZero(paid, paidAmount),
                orZero(refunded, refundCount), orZero(refunded, refundAmount));
    }

    private static long orZero(Tuple row, NumberExpression<Long> expression) {
        Long value = row == null ? null : row.get(expression);
        return value == null ? 0L : value;
    }
}