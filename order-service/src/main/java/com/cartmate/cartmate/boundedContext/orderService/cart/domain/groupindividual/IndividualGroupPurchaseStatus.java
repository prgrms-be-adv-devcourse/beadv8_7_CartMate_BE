package com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IndividualGroupPurchaseStatus {
    OPEN("모집 중"),
    CLOSED("마감");

    private final String label;
}
