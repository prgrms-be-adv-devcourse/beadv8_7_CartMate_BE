package com.cartmate.cartmate.boundedContext.member.out;

import java.time.LocalDate;

// 관리자 대시보드에 필요한 결제 집계. payment 모듈 호출은 어댑터에서만 한다.
public interface AdminDashboardPaymentClient {

    PaymentStats getDailyStats(LocalDate date);

    record PaymentStats(long paidCount, long paidAmount, long refundCount, long refundAmount) {}
}