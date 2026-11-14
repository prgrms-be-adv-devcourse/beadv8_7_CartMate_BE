package com.cartmate.cartmate.shared.delivery;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PurchaseType {
    INDIVIDUAL_GROUP("개인 주문"),
    SHARED_GROUP("공동 주문");

    private final String label;
}
