package com.cartmate.cartmate.boundedContext.payment.out;

import com.cartmate.cartmate.boundedContext.payment.dto.PaymentDailySummary;

import java.time.LocalDateTime;

public interface PaymentRepositoryCustom {

    // [from, to) 기간의 결제 건수·금액과 환불 건수·금액 (결제 1번 + 환불 1번 조회)
    PaymentDailySummary summarize(LocalDateTime from, LocalDateTime to);
}