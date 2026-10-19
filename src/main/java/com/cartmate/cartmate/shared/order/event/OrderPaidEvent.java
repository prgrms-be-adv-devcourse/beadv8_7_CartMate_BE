package com.cartmate.cartmate.shared.order.event;

import java.time.LocalDateTime;
import java.util.List;

public record OrderPaidEvent(
        Long orderId,
        Long memberId,
        String orderType,
        Long individualGroupPurchaseId,
        LocalDateTime paidAt,
        List<Item> items
) {

    public record Item(
            Long productId,
            Long inventoryId,
            int quantity,
            long unitPrice
    ) {
    }
}
