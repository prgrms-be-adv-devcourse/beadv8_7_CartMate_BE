package com.cartmate.cartmate.global.event;

public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
