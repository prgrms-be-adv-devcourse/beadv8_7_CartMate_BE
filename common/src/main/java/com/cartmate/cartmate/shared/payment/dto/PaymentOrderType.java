package com.cartmate.cartmate.shared.payment.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 어떤 주문의 결제인지. 결제 서버는 주문 내용을 모르고 이 구분과 주문 ID로만 결제를 구별한다.
@Getter
@RequiredArgsConstructor
public enum PaymentOrderType {
    INDIVIDUAL_GROUP("개인 주문"),
    SHARED_GROUP("공동 주문");  // 공동 주문 분담금 결제

    private final String label;
}