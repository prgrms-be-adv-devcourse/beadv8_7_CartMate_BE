package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.shared.order.OrderType;

import com.cartmate.cartmate.shared.order.OrderCancelReason;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

public record OrderCanceledEvent(
        Long orderId,
        Long memberId,
        OrderType orderType,
        OrderCancelReason cancelReason,
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
