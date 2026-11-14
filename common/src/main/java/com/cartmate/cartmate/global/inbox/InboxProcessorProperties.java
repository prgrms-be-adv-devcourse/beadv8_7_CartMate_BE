package com.cartmate.cartmate.global.inbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "custom.inbox.processor")
public record InboxProcessorProperties(
        @DefaultValue("PT1S") Duration fixedDelay,
        @DefaultValue("PT1S") Duration initialDelay,
        @DefaultValue("100") int batchSize,
        @DefaultValue("15") int maxAttempts,
        @DefaultValue("PT1S") Duration initialBackoff,
        @DefaultValue("PT10M") Duration maxBackoff
) {

    private static final int MAX_SHIFT = 30;

    public Duration backoffAfter(int attempts) {
        if (attempts < 1) {
            return Duration.ZERO;
        }
        int shift = Math.min(attempts - 1, MAX_SHIFT);
        Duration delay = initialBackoff.multipliedBy(1L << shift);
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }
}
