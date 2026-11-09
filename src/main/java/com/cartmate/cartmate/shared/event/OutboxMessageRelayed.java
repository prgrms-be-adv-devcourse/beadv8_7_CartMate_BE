package com.cartmate.cartmate.shared.event;

/**
 * 아웃박스 릴레이(in-process 싱크)가 NEW 행을 집어 다시 발행하는 Spring 이벤트.
 *
 * <p>기존 리스너는 원본 이벤트 타입({@code OrderPaidEvent} 등)을 구독하므로 이 래퍼를 받지 않는다.
 * 읽기 모델 같은 새 consumer가 이 래퍼를 구독하고, {@code event()}의 타입으로 분기하며,
 * {@code envelope().eventId()}로 멱등 처리한다(릴레이는 at-least-once: 싱크 성공 직후 상태 갱신이 실패하면 다시 온다).
 *
 * <p>순서 보장은 없다: 실패한 행은 NEW로 남아 다시 시도되는 동안 같은 애그리거트의 뒤 이벤트가 먼저 갈 수 있다
 * (예: OrderPaidEvent 재시도 중 OrderCanceledEvent 전달). consumer는 이벤트 순서가 아니라 자기 상태로 판단한다.
 *
 * <p>릴레이는 트랜잭션 밖에서 발행한다. cart · order의 consumer는 ArchUnit R4(같은 TX {@code @EventListener} 금지) 때문에
 * {@code @TransactionalEventListener(fallbackExecution = true)}로 받는다. 트랜잭션이 없으니 바로 실행되고,
 * 예외는 그대로 릴레이로 올라가 재시도된다. consumer가 자기 트랜잭션을 연다.
 * 이 래퍼를 트랜잭션 안에서 발행하면 커밋 뒤 실행으로 바뀌어 예외가 로그로만 남으니, 릴레이 밖에서 발행하지 않는다.
 */
public record OutboxMessageRelayed(EventEnvelope envelope, DomainEvent event) {

    public <T extends DomainEvent> boolean is(Class<T> type) {
        return type.isInstance(event);
    }
}
