package com.cartmate.cartmate.boundedContext.payment.domain;

public enum PaymentRefundReason {
    CLAIM_REFUND,        // 판매자가 승인한 취소·반품 요청
    BUYER_CANCEL,        // 판매자 확인 전 구매자 취소 (시스템 자동)
    SHARED_GROUP_CANCEL, // 공동물품 구매 무산 (시스템 자동)
    ADMIN,               // 관리자 수동 환불
    PRODUCT_HIDDEN       // 판매글 판매 중지·숨김·삭제로 발송 전 주문 자동 환불
}