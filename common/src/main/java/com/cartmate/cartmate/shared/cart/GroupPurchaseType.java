package com.cartmate.cartmate.shared.cart;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GroupPurchaseType {
    INDIVIDUAL("공동구매-개인물품"),
    SHARED("공동구매-공동물품");

    private final String description;
}
