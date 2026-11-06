package com.cartmate.cartmate.shared.cart.event;

import com.cartmate.cartmate.shared.cart.PaybackOverlapPolicy;
import com.cartmate.cartmate.shared.cart.dto.PaybackConditionPayload;

import com.cartmate.cartmate.shared.cart.IndividualGroupPurchaseOrigin;

import com.cartmate.cartmate.shared.cart.IndividualGroupPurchaseCloseReason;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.List;

public record IndividualGroupPurchaseClosedEvent(
        Long individualGroupPurchaseId,
        IndividualGroupPurchaseOrigin origin,
        IndividualGroupPurchaseCloseReason closeReason,
        LocalDateTime deadlineAt,
        LocalDateTime closedAt,
        long revision,
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
