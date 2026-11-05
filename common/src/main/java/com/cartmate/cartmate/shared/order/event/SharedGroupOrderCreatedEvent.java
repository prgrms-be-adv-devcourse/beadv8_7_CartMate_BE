package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

// 공동구매 마감 이벤트를 받아 공동 주문을 만들었다. cart가 공동구매를 CLOSED → ORDER_CREATED로 바꾼다.
// 같은 공동구매로 다시 마감 이벤트가 오면(중복 전달 · 재마감) 이미 있는 주문으로 다시 알린다
public record SharedGroupOrderCreatedEvent(
        Long sharedGroupOrderId,
        Long sharedGroupPurchaseId,
        LocalDateTime createdAt
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "SharedGroupOrder";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(sharedGroupOrderId);
    }
}
