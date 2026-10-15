package com.cartmate.cartmate.domain.cart.groupshared.event;

import com.cartmate.cartmate.domain.cart.groupshared.entity.SharedGroupPurchase;
import com.cartmate.cartmate.domain.cart.groupshared.entity.SharedGroupPurchaseMember;

import java.time.LocalDateTime;
import java.util.List;


public record SharedGroupPurchaseClosedEvent(
        Long sharedGroupPurchaseId,
        Long creatorMemberId,
        List<Long> participantMemberIds,
        List<Item> items,
        LocalDateTime closedAt
) {


    public static SharedGroupPurchaseClosedEvent from(SharedGroupPurchase purchase) {
        List<Long> participantMemberIds = purchase.getMembers().stream()
                .map(SharedGroupPurchaseMember::getMemberId)
                .toList();
        List<Item> items = purchase.getItems().stream()
                .map(item -> new Item(item.getProductId(), item.getInventoryId(), item.getQuantity()))
                .toList();
        return new SharedGroupPurchaseClosedEvent(
                purchase.getId(),
                purchase.getCreatorMemberId(),
                participantMemberIds,
                items,
                purchase.getClosedAt()
        );
    }

    public record Item(
            Long productId,
            Long inventoryId,
            int quantity
    ) {
    }
}
