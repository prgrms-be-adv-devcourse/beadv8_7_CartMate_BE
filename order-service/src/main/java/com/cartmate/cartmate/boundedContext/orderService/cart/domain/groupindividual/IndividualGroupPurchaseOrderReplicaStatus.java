package com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IndividualGroupPurchaseOrderReplicaStatus {
    PENDING("결제 대기"),
    PAID("결제 완료"),
    CANCELED("취소");

    private final String label;
}
