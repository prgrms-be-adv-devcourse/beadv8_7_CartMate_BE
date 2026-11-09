package com.cartmate.cartmate.boundedContext.orderService.abuse.simulation;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.util.List;

record SimulatedAction(DomainEvent event, List<Long> actorIds) {
}
