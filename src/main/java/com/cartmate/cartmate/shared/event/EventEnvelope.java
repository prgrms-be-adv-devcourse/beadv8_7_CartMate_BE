package com.cartmate.cartmate.shared.event;

import java.time.LocalDateTime;

/**
 * 아웃박스가 이벤트에 씌우는 봉투. 릴레이 싱크와 consumer가 보는 메타데이터.
 * Kafka로 넘어가면 payload가 메시지 값, aggregateId가 키, 나머지가 헤더가 된다.
 *
 * @param eventId       발행 1건마다 다른 UUID. consumer는 이 값으로 중복 처리를 막는다(at-least-once)
 * @param eventType     이벤트 클래스 단순 이름 (예: OrderPaidEvent)
 * @param topic         {@code cartmate.<발행 모듈>.<이벤트 kebab-case>}
 * @param aggregateType 예: Order
 * @param aggregateId   예: 주문 ID
 * @param occurredAt    발행 시각
 * @param version       봉투 · 이벤트 스키마 버전
 * @param payload       이벤트 JSON
 */
public record EventEnvelope(
        String eventId,
        String eventType,
        String topic,
        String aggregateType,
        String aggregateId,
        LocalDateTime occurredAt,
        int version,
        String payload
) {
}
