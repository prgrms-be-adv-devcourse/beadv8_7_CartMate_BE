package com.cartmate.cartmate.shared.delivery.event;

import com.cartmate.cartmate.global.event.DomainEvent;

public record DeliveryOrderCanceledEvent(
        Long orderId,
        int requestSeq
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "DeliveryOrder";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(orderId);
    }
}
