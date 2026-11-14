package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;

import java.time.LocalDateTime;
import java.util.List;

public record SharedGroupOrderConfirmedEvent(
        Long sharedGroupOrderId,
        Long sharedGroupPurchaseId,
        Long creatorMemberId,
        LocalDateTime confirmedAt,
        TeamDeliveryAddress teamDeliveryAddress,
        List<Item> items
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "SharedGroupOrder";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(sharedGroupOrderId);
    }

    public record Item(
            Long orderItemId,
            Long productId,
            Long inventoryId,
            Long sellerId,
            String productName,
            String optionName,
            int quantity,
            long unitPrice
    ) {
    }
}
