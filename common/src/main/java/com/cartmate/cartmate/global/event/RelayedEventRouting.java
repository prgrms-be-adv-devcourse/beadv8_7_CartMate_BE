package com.cartmate.cartmate.global.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RelayedEventRouting {

    private final boolean kafkaEnabled;

    public RelayedEventRouting(@Value("${cartmate.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaEnabled = kafkaEnabled;
    }

    public boolean accepts(OutboxMessageRelayed message) {
        return message.fromKafka() == kafkaEnabled;
    }
}
