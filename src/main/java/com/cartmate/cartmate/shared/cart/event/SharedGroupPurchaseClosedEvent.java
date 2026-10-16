package com.cartmate.cartmate.shared.cart.event;

import java.time.LocalDateTime;
import java.util.List;

public record SharedGroupPurchaseClosedEvent(
        Long sharedGroupPurchaseId,
        Long creatorMemberId,
        List<Long> participantMemberIds,
        List<Item> items,
        LocalDateTime closedAt
) {

    public record Item(
            Long productId,
            Long inventoryId,
            int quantity
    ) {
    }
}
