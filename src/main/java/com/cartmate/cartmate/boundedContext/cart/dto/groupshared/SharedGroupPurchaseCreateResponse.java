package com.cartmate.cartmate.boundedContext.cart.dto.groupshared;

import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchase;

import java.util.Set;


public record SharedGroupPurchaseCreateResponse(
        Long id,
        String inviteCode,
        Set<Long> excludedInventoryIds
) {


    public static SharedGroupPurchaseCreateResponse from(SharedGroupPurchase purchase) {
        return new SharedGroupPurchaseCreateResponse(purchase.getId(), purchase.getInviteCode(), Set.of());
    }

    public static SharedGroupPurchaseCreateResponse duplicated(SharedGroupPurchase purchase,
                                                               Set<Long> excludedInventoryIds) {
        return new SharedGroupPurchaseCreateResponse(purchase.getId(), purchase.getInviteCode(),
                Set.copyOf(excludedInventoryIds));
    }
}
