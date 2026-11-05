package com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;


public record IndividualGroupPurchaseCreateResponse(
        Long id,
        String inviteCode
) {

    public static IndividualGroupPurchaseCreateResponse from(IndividualGroupPurchase purchase) {
        return new IndividualGroupPurchaseCreateResponse(purchase.getId(), purchase.getInviteCode());
    }
}
