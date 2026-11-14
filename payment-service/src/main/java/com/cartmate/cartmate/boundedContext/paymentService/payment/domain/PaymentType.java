package com.cartmate.cartmate.boundedContext.paymentService.payment.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentType {
    INDIVIDUAL_GROUP("개인 주문"),  // 일반 주문 + 개인물품 공동구매 주문
    SHARED_GROUP("공동 주문");      // 공동물품 구매 분담금

    private final String label;
}