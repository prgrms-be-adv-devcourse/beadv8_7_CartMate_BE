package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.shared.event.EventEnvelope;

/**
 * 릴레이가 NEW 행을 넘기는 곳. 예외를 던지면 실패로 기록되고 다음 폴링에 다시 시도된다.
 * 지금은 {@link InProcessOutboxRelaySink}(Spring 이벤트 재발행)뿐이고, Kafka 싱크는 인프라가 생기면 추가한다.
 */
public interface OutboxRelaySink {

    void send(EventEnvelope envelope);
}
