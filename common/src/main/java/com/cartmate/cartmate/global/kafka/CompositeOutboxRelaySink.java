package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.outbox.InProcessOutboxRelaySink;
import com.cartmate.cartmate.global.outbox.OutboxRelaySink;

/**
 * Kafka를 켠 서비스의 싱크: 같은 서비스 안(in-process)에 먼저 전달하고, 이어서 Kafka로 내보낸다.
 * 서비스 안 소비는 지금처럼 같은 JVM에서 받고, 다른 서비스는 Kafka로 받는다(MSA-5 단계적 전환 1단계).
 *
 * <p>순서를 in-process → Kafka로 둔 이유: 서비스 안 처리가 실패하면 Kafka로 내보내지 않고 행 전체를 다시 시도한다.
 * 반대로 Kafka 전송이 실패하면 다음 시도 때 서비스 안 리스너도 다시 돈다. 그래서 서비스 안 리스너는 도메인 상태나
 * {@code ProcessedEvents}로 중복을 견뎌야 한다(지금 리스너들은 모두 그렇다).
 * 서비스 안 소비를 Kafka consumer로 옮기면(MSA-9 · MSA-10) in-process 쪽은 빈 전달이 되어 이 문제도 사라진다.
 */
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
