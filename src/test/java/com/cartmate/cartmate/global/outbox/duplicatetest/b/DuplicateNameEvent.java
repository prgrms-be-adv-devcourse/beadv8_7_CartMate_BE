package com.cartmate.cartmate.global.outbox.duplicatetest.b;

import com.cartmate.cartmate.global.event.DomainEvent;

/** DomainEventTypeRegistryTest 전용: 단순 이름이 겹치는 이벤트 쌍의 하나 */
public record DuplicateNameEvent(Long id) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "Duplicate";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(id);
    }
}
