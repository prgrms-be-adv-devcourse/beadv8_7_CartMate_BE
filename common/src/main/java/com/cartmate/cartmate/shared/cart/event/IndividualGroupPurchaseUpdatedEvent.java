package com.cartmate.cartmate.shared.cart.event;

import com.cartmate.cartmate.shared.cart.PaybackOverlapPolicy;
import com.cartmate.cartmate.shared.cart.dto.PaybackConditionPayload;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.List;

public record IndividualGroupPurchaseUpdatedEvent(
        Long individualGroupPurchaseId,
        LocalDateTime deadlineAt,
        List<Long> productIds,
        long revision,
        LocalDateTime updatedAt,
        PaybackOverlapPolicy paybackOverlapPolicy,
        List<PaybackConditionPayload> paybackConditions
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "IndividualGroupPurchase";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(individualGroupPurchaseId);
    }
}
