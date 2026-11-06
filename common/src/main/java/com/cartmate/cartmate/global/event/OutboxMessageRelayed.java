package com.cartmate.cartmate.global.event;

public record OutboxMessageRelayed(EventEnvelope envelope, DomainEvent event, boolean fromKafka) {

    public OutboxMessageRelayed(EventEnvelope envelope, DomainEvent event) {
        this(envelope, event, false);
    }

    public <T extends DomainEvent> boolean is(Class<T> type) {
        return type.isInstance(event);
    }
}
