package com.cartmate.cartmate.global.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RelayedEventRoutingTest {

    private static final OutboxMessageRelayed IN_PROCESS = new OutboxMessageRelayed(null, null, false);
    private static final OutboxMessageRelayed FROM_KAFKA = new OutboxMessageRelayed(null, null, true);

    @Test
    @DisplayName("Kafka가 꺼져 있으면 서비스 안 전달만 받고 Kafka 재전달은 받지 않는다")
    void kafkaDisabled() {
        RelayedEventRouting routing = new RelayedEventRouting(false);

        assertThat(routing.accepts(IN_PROCESS)).isTrue();
        assertThat(routing.accepts(FROM_KAFKA)).isFalse();
    }

    @Test
    @DisplayName("Kafka가 켜져 있으면 Kafka 재전달만 받고 서비스 안 전달은 무시해 이중 처리를 막는다")
    void kafkaEnabled() {
        RelayedEventRouting routing = new RelayedEventRouting(true);

        assertThat(routing.accepts(FROM_KAFKA)).isTrue();
        assertThat(routing.accepts(IN_PROCESS)).isFalse();
    }
}
