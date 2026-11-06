package com.cartmate.cartmate.global.event;

/**
 * cart · order UseCase가 이벤트를 내보낼 때 쓰는 포트. Spring의 {@code ApplicationEventPublisher}를 직접 주입하지 않는다.
 *
 * 트랜잭션이 롤백되면 outbox 행도 사라진다. 활성 트랜잭션 없이 부르면 예외.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
