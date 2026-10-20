package com.cartmate.cartmate.boundedContext.payment.domain;

public enum PaymentType {
    INDIVIDUAL,  // 개인 주문 (일반 주문 + 개인물품 공동구매 주문)
    GROUP_SHARE  // 공동물품 구매 분담금
}