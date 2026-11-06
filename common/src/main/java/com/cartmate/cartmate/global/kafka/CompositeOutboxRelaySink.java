package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.outbox.InProcessOutboxRelaySink;
import com.cartmate.cartmate.global.outbox.OutboxRelaySink;

public class CompositeOutboxRelaySink implements OutboxRelaySink {

    private final InProcessOutboxRelaySink inProcess;
    private final KafkaOutboxRelaySink kafka;

    public CompositeOutboxRelaySink(InProcessOutboxRelaySink inProcess, KafkaOutboxRelaySink kafka) {
        this.inProcess = inProcess;
        this.kafka = kafka;
    }

    @Override
    public void send(EventEnvelope envelope) {
        inProcess.send(envelope);
        kafka.send(envelope);
    }
}
