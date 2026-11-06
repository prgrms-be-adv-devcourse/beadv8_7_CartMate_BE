package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.global.event.EventEnvelope;

/**
 * 릴레이가 NEW 행을 넘기는 곳. 예외를 던지면 실패로 기록되고 다음 폴링에 다시 시도된다.
 */
public interface OutboxRelaySink {

    void send(EventEnvelope envelope);
}
