package com.cartmate.cartmate.global.event;

public record OutboxMessageRelayed(EventEnvelope envelope, DomainEvent event) {

    public <T extends DomainEvent> boolean is(Class<T> type) {
        return type.isInstance(event);
    }
}
