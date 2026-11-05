package com.cartmate.cartmate.shared.test.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

/** 아웃박스 통합 테스트 전용 이벤트. 실제 리스너가 하나도 구독하지 않아 다른 모듈 테이블을 건드리지 않는다. */
public record OutboxProbeEvent(Long probeId, String note, LocalDateTime at) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "OutboxProbe";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(probeId);
    }
}
