package com.cartmate.cartmate.global.event;

/**
 * 모듈 밖으로 내보내는 도메인 이벤트가 구현한다. (cart · order가 발행하는 shared 이벤트)
 *
 * <p>아웃박스 봉투의 aggregate 정보로 쓰인다: 어떤 애그리거트(예: Order)의 어느 행(예: 주문 ID)에서
 * 난 일인지. Kafka로 넘어가면 {@link #aggregateId()}가 파티션 키가 되어 같은 애그리거트의 이벤트 순서를 지킨다.
 *
 * <p>이벤트 record의 필드는 그대로 두고 메서드만 더한다. 토픽 이름은 클래스 위치와 이름에서 정해진다
 * ({@code shared.<모듈>.event.<이름>Event} → {@code cartmate.<모듈>.<이름-kebab-case>}).
 */
public interface DomainEvent {

    String aggregateType();

    String aggregateId();
}
