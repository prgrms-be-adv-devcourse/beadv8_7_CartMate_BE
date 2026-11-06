package com.cartmate.cartmate.shared.cart.dto;

import java.time.LocalDateTime;

public record IndividualGroupPurchaseActivity(
        Long id,
        String title,
        boolean open,
        int productCount,
        LocalDateTime deadlineAt
) {
}
