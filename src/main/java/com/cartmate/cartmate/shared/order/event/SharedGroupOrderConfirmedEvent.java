package com.cartmate.cartmate.shared.order.event;

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
) {

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
