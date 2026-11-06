package com.cartmate.cartmate.global.event;

public interface DomainEvent {

    String aggregateType();

    String aggregateId();
}
