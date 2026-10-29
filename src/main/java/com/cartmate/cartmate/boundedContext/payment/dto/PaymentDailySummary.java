package com.cartmate.cartmate.boundedContext.payment.dto;

// 관리자 대시보드용 하루 결제·환불 집계
public record PaymentDailySummary(
        long paidCount,
        long paidAmount,
        long refundCount,
        long refundAmount
) {
}