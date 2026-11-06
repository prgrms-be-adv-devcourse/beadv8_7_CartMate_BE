package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.shared.order.OrderType;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        String orderNumber,
        Long memberId,
        OrderType orderType,
        Long individualGroupPurchaseId,
        LocalDateTime createdAt,
        List<Item> items
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "Order";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(orderId);
    }

    public record Item(
            Long orderItemId,
            Long productId,
            Long inventoryId,
            Long sellerId,
            int quantity,
            long unitPrice
    ) {
    }
}
