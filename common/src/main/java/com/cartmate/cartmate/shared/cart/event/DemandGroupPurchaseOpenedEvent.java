package com.cartmate.cartmate.shared.cart.event;

import com.cartmate.cartmate.shared.cart.PaybackOverlapPolicy;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

public record DemandGroupPurchaseOpenedEvent(
        Long individualGroupPurchaseId,
        Long productId,
        String title,
        String inviteCode,
        LocalDateTime deadlineAt,
        int paybackMinQuantity,
        long paybackAmountPerUnit,
        PaybackOverlapPolicy paybackOverlapPolicy,
        LocalDateTime openedAt
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
