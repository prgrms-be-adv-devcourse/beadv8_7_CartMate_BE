package com.cartmate.cartmate.shared.cart.dto;

import java.time.LocalDateTime;

public record IndividualGroupPurchaseOrderable(
        Long individualGroupPurchaseId,
        LocalDateTime saleDeadlineAt
) {
}
