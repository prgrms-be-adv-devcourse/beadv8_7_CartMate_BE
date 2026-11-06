package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DomainEventPublisher}의 아웃박스 구현. 호출한 트랜잭션에 참여만 한다(MANDATORY).
 */
@Component
@RequiredArgsConstructor
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(DomainEvent event) {
        outboxEventRepository.save(outboxEventFactory.create(event));
    }
}
