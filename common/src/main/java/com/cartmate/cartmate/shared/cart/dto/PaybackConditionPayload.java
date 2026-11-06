package com.cartmate.cartmate.shared.cart.dto;

import com.cartmate.cartmate.shared.cart.PaybackFunder;

public record PaybackConditionPayload(
        Long productId,
        PaybackFunder funder,
        Long sellerId,
        int minQuantity,
        long amountPerUnit
) {
}
