package com.cartmate.cartmate.global.event;

/**
 * cart · order UseCase가 이벤트를 내보낼 때 쓰는 포트. Spring의 {@code ApplicationEventPublisher}를 직접 주입하지 않는다.
 *
 * <p>구현({@code global.outbox.OutboxDomainEventPublisher})은 호출한 트랜잭션 안에서 outbox 행을 저장하고,
 * 지금은 기존 리스너를 위해 같은 이벤트를 Spring 이벤트로도 발행한다(이중 발행).
 * 트랜잭션이 롤백되면 outbox 행도 사라진다. 활성 트랜잭션 없이 부르면 예외.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
