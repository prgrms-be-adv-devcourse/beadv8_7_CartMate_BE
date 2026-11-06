package com.cartmate.cartmate.shared.cart.dto;

import java.time.LocalDateTime;
import java.util.List;

public record IndividualGroupPurchaseSnapshot(
        Long individualGroupPurchaseId,
        boolean closed,
        LocalDateTime deadlineAt,
        LocalDateTime closedAt,
        List<Long> productIds,
        long revision
) {
}
