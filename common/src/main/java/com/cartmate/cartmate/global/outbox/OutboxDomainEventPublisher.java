package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DomainEventPublisher}의 아웃박스 구현. 호출한 트랜잭션에 참여만 한다(MANDATORY).
 *
 * <p>outbox 행만 저장한다. 받는 쪽은 릴레이가 커밋된 행을 읽어 전달한 것({@code OutboxMessageRelayed} · Kafka)으로만 받는다.
 * 이행 기간에 두던 이중 발행(같은 이벤트를 Spring 이벤트로 한 번 더)은 서비스 분리로 받는 쪽이 같은 프로세스에 없어져
 * 없앴다(MSA-5). 그래서 이벤트는 발행한 트랜잭션이 커밋돼야만 나가고, 같은 트랜잭션 안에서 받는 리스너는 없다.
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
