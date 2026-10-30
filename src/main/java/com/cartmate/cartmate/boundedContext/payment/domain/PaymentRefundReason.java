package com.cartmate.cartmate.boundedContext.payment.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 환불 사유
@Getter
@RequiredArgsConstructor
public enum PaymentRefundReason {
    CLAIM_REFUND("판매자가 승인한 취소·반품 요청"),
    BUYER_CANCEL("판매자 확인 전 구매자 취소"),
    SHARED_GROUP_CANCEL("공동 주문 무산·취소"),
    ADMIN("관리자 수동 환불"),
    PRODUCT_HIDDEN("판매글 판매 중지로 발송 전 주문 자동 환불");

    // 화면·로그에 쓰는 설명. 새 값을 추가할 때 설명이 없으면 컴파일되지 않는다.
    private final String description;
}