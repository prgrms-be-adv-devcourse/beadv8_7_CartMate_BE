package com.cartmate.cartmate.boundedContext.cart.dto;

import com.cartmate.cartmate.boundedContext.cart.domain.IndividualGroupPurchase;


public record IndividualGroupPurchaseCreateResponse(
        Long id,
        String inviteCode
) {

    public static IndividualGroupPurchaseCreateResponse from(IndividualGroupPurchase purchase) {
        return new IndividualGroupPurchaseCreateResponse(purchase.getId(), purchase.getInviteCode());
    }
}
