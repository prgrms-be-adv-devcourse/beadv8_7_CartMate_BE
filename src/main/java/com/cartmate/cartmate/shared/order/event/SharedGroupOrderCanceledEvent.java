package com.cartmate.cartmate.shared.order.event;

import java.time.LocalDateTime;
import java.util.List;

public record SharedGroupOrderCanceledEvent(
        Long sharedGroupOrderId,
        Long sharedGroupPurchaseId,
        String cancelReason,
        LocalDateTime canceledAt,
        List<Long> unpaidMemberIds
) {
}
