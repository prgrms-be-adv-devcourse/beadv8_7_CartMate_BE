package com.cartmate.cartmate.shared.cart;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GroupPurchaseType {
    INDIVIDUAL("개인물품 공동구매"),
    SHARED("공동물품 공동구매");

    private final String label;
}
