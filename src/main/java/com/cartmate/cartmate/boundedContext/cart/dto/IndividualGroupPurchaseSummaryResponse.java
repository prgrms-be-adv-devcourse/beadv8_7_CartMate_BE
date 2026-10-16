package com.cartmate.cartmate.boundedContext.cart.dto;

import com.cartmate.cartmate.boundedContext.cart.domain.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.IndividualGroupPurchaseStatus;

import java.time.LocalDateTime;


public record IndividualGroupPurchaseSummaryResponse(
        Long id,
        String title,
        IndividualGroupPurchaseStatus status,
        boolean open,
        int productCount,
        LocalDateTime deadlineAt
) {

    public static IndividualGroupPurchaseSummaryResponse of(IndividualGroupPurchase purchase, LocalDateTime now) {
        return new IndividualGroupPurchaseSummaryResponse(
                purchase.getId(),
                purchase.getTitle(),
                purchase.getStatus(),
                purchase.isOpen(now),
                purchase.getProducts().size(),
                purchase.getDeadlineAt()
        );
    }
}
