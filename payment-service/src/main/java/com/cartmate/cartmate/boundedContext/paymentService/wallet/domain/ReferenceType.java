package com.cartmate.cartmate.boundedContext.paymentService.wallet.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReferenceType {
    CHARGE_ORDER("충전 주문"),
    PAYMENT("결제"),
    REFUND("환불"),
    SETTLEMENT("정산"),
    WITHDRAW_ORDER("출금 주문");

    private final String label;
}
