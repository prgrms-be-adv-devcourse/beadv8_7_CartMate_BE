package com.cartmate.cartmate.shared.cart.event;

import java.time.LocalDateTime;

public record DemandGroupPurchaseOpenedEvent(
        Long individualGroupPurchaseId,
        Long productId,
        String title,
        String inviteCode,
        LocalDateTime deadlineAt,
        int paybackMinBuyers,
        long paybackAmount,
        LocalDateTime openedAt
) {
}
