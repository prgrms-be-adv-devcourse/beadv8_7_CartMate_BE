package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.shared.event.DomainEvent;

// 공동구매 마감 이벤트로 공동 주문을 만들려 했지만 다시 해도 같은 이유로 실패한다(예: 상품을 찾을 수 없음).
// cart가 공동구매를 CLOSED → FROZEN으로 돌린다. 주문이 없으니 애그리거트는 공동구매다
public record SharedGroupOrderCreationFailedEvent(
        Long sharedGroupPurchaseId,
        String reason
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "SharedGroupPurchase";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(sharedGroupPurchaseId);
    }
}
