package com.cartmate.cartmate.boundedContext.cart.domain.groupindividual;

// 결제 대기 → 결제 완료로만 나아가고, 취소는 끝이다. 이벤트가 늦게 오거나 순서가 바뀌어도 뒤로 가지 않는다
public enum IndividualGroupPurchaseOrderReplicaStatus {
    PENDING,
    PAID,
    CANCELED
}
