package com.cartmate.cartmate.boundedContext.order.out.personal;

import java.time.LocalDateTime;

public record IndividualGroupPurchaseOrderLineView(
        Long orderId,
        Long buyerMemberId,
        Long productId,
        Long inventoryId,
        int quantity,
        long unitPrice,
        LocalDateTime orderedAt
) {
}
