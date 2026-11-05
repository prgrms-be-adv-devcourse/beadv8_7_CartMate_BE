package com.cartmate.cartmate.boundedContext.orderService.shared.order.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

public record OrderCanceledEvent(
        Long orderId,
        Long memberId,
        String orderType,
        String cancelReason,
        boolean paid,
        LocalDateTime canceledAt
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "Order";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(orderId);
    }
}
