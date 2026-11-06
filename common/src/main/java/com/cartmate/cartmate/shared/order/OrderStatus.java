package com.cartmate.cartmate.shared.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {
    PAYMENT_PENDING("결제 대기"),
    PAID("결제 완료"),
    CANCELED("취소");

    private final String description;
}
