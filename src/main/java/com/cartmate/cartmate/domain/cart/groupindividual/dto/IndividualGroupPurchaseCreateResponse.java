package com.cartmate.cartmate.domain.cart.groupindividual.dto;

import com.cartmate.cartmate.domain.cart.groupindividual.entity.IndividualGroupPurchase;


public record IndividualGroupPurchaseCreateResponse(
        Long id,
        String inviteCode
) {

    public static IndividualGroupPurchaseCreateResponse from(IndividualGroupPurchase purchase) {
        return new IndividualGroupPurchaseCreateResponse(purchase.getId(), purchase.getInviteCode());
    }
}
