package com.cartmate.cartmate.shared.event;

/**
 * 아웃박스 릴레이(in-process 싱크)가 NEW 행을 집어 다시 발행하는 Spring 이벤트.
 *
 * <p>기존 리스너는 원본 이벤트 타입({@code OrderPaidEvent} 등)을 구독하므로 이 래퍼를 받지 않는다.
 * 읽기 모델 같은 새 consumer가 이 래퍼를 {@code @EventListener}로 구독하고, {@code event()}의 타입으로 분기하며,
 * {@code envelope().eventId()}로 멱등 처리한다(릴레이는 at-least-once: 싱크 성공 직후 상태 갱신이 실패하면 다시 온다).
 *
 * <p>순서 보장은 없다: 실패한 행은 NEW로 남아 다시 시도되는 동안 같은 애그리거트의 뒤 이벤트가 먼저 갈 수 있다
 * (예: OrderPaidEvent 재시도 중 OrderCanceledEvent 전달). consumer는 이벤트 순서가 아니라 자기 상태로 판단한다.
 *
 * <p>릴레이는 트랜잭션 밖에서 발행하므로 {@code @TransactionalEventListener}로는 받을 수 없다.
 * consumer가 자기 트랜잭션을 연다.
 */
public record OutboxMessageRelayed(EventEnvelope envelope, DomainEvent event) {

    public <T extends DomainEvent> boolean is(Class<T> type) {
        return type.isInstance(event);
    }
}
