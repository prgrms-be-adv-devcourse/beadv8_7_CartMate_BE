package com.cartmate.cartmate.boundedContext.member.out;

import java.time.LocalDate;

/**
 * 회원 모듈이 결제 서버(결제·예치금·정산)에 묻는 창구. 관리자 대시보드·마이페이지·탈퇴 확인이 같이 쓴다.
 * 구현은 cartmate.payment.client 로 고른다 — local: 같은 앱 Facade, http: 결제 서버 내부 API.
 */
public interface MemberPaymentServerClient {

    // 관리자 대시보드: 하루 결제·환불 집계
    PaymentStats getDailyPaymentStats(LocalDate date);

    // 마이페이지: 예치금 잔액
    long getWalletBalance(Long memberId);

    // 회원 탈퇴 확인: 판매자의 지급 전 정산 수
    long countPendingPayouts(Long sellerId);

    // 필드 이름을 결제 서버 응답(PaymentDailySummary)과 맞춰 HTTP 응답을 그대로 읽는다
    record PaymentStats(long paidCount, long paidAmount, long refundCount, long refundAmount) {}
}