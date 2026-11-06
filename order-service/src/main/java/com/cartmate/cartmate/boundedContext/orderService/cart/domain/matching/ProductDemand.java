package com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching;

import java.util.Set;

public record ProductDemand(
        Long productId,
        Set<Long> memberIds
) {

    public ProductDemand {
        if (productId == null) {
            throw new IllegalArgumentException("productId is required");
        }
        memberIds = Set.copyOf(memberIds);
    }
}
