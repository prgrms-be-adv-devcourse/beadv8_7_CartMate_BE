package com.cartmate.cartmate.boundedContext.cart.out.groupindividual;

import java.time.LocalDateTime;


public record IndividualGroupPurchaseOrderLine(
        Long orderId,
        Long buyerMemberId,
        Long productId,
        Long inventoryId,
        int quantity,
        long unitPrice,
        LocalDateTime orderedAt
) {
    public long amount() {
        return unitPrice * quantity;
    }
}
