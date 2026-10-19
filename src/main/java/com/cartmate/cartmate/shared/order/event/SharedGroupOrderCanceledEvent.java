package com.cartmate.cartmate.shared.order.event;

import java.time.LocalDateTime;

public record SharedGroupOrderCanceledEvent(
        Long sharedGroupOrderId,
        Long sharedGroupPurchaseId,
        String cancelReason,
        LocalDateTime canceledAt
) {
}
