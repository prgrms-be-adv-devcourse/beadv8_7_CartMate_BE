package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.shared.order.SharedGroupOrderCancelReason;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.List;

public record SharedGroupOrderCanceledEvent(
        Long sharedGroupOrderId,
        Long sharedGroupPurchaseId,
        SharedGroupOrderCancelReason cancelReason,
        LocalDateTime canceledAt,
        List<Long> unpaidMemberIds
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "SharedGroupOrder";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(sharedGroupOrderId);
    }
}
