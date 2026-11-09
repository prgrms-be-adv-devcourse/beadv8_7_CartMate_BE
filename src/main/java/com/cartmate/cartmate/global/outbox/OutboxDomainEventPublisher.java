package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.shared.event.DomainEvent;
import com.cartmate.cartmate.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DomainEventPublisher}의 아웃박스 구현. 호출한 트랜잭션에 참여만 한다(MANDATORY).
 *
 * <p>순서: (a) outbox 행 저장 → (b) 기존 리스너를 위해 같은 이벤트를 Spring 이벤트로도 발행.
 * (b)는 이행 기간의 이중 발행이다. delivery · payment · abuse의 같은 TX {@code @EventListener}와
 * cart의 AFTER_COMMIT 리스너가 지금 동작을 유지해야 하고, 이 리스너들을 아웃박스 consumer로 옮기는 일은
 * ARCH-7 · ARCH-9가 담당자와 협의해 한다. 그때 (b)를 뺀다.
 *
 * <p>(a)를 먼저 하는 이유: (b)의 같은 TX 리스너가 예외를 던지면 둘 다 롤백돼야 하고,
 * 리스너가 outbox 행을 보려 해도 이미 있어야 한다.
 */
@Component
@RequiredArgsConstructor
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(DomainEvent event) {
        outboxEventRepository.save(outboxEventFactory.create(event));
        applicationEventPublisher.publishEvent(event);
    }
}
