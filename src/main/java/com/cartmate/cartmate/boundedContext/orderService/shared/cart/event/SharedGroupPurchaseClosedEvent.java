package com.cartmate.cartmate.boundedContext.orderService.shared.cart.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.List;

public record SharedGroupPurchaseClosedEvent(
        Long sharedGroupPurchaseId,
        Long creatorMemberId,
        List<Long> participantMemberIds,
        List<Item> items,
        LocalDateTime closedAt
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "SharedGroupPurchase";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(sharedGroupPurchaseId);
    }

    public record Item(
            Long productId,
            Long inventoryId,
            int quantity
    ) {
    }
}
