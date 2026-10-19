package com.cartmate.cartmate.boundedContext.payment.domain;

public enum PaymentRefundReason {
    ORDER_CANCEL,  // 배송 전 구매 취소 (전액)
    CLAIM_REFUND,  // 반품·환불 요청 승인
    ADMIN          // 관리자 수동 환불
}