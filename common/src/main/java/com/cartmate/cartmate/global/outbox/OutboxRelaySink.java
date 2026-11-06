package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.global.event.EventEnvelope;

public interface OutboxRelaySink {

    void send(EventEnvelope envelope);
}
