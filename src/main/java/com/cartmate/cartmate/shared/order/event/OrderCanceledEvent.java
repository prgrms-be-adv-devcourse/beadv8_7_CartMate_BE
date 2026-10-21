package com.cartmate.cartmate.shared.order.event;

import java.time.LocalDateTime;

public record OrderCanceledEvent(
        Long orderId,
        Long memberId,
        String orderType,
        String cancelReason,
        boolean paid,
        LocalDateTime canceledAt
) {
}
