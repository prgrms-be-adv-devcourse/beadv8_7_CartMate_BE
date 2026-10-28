package com.cartmate.cartmate.boundedContext.abuse.out;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MemoryAbuseSignalCounter implements AbuseSignalCounter {

    private final Map<CounterKey, Map<String, LocalDateTime>> signals = new ConcurrentHashMap<>();

    @Override
    public boolean record(AbuseSignal signal, Duration retention) {
        LocalDateTime expiredBefore = signal.occurredAt().minus(retention);
        AtomicBoolean added = new AtomicBoolean();
        signals.compute(new CounterKey(signal.memberId(), signal.type()), (key, entries) -> {
            Map<String, LocalDateTime> next = entries == null ? new ConcurrentHashMap<>() : entries;
            next.values().removeIf(occurredAt -> occurredAt.isBefore(expiredBefore));
            added.set(next.putIfAbsent(signal.sourceKey(), signal.occurredAt()) == null);
            return next;
        });
        return added.get();
    }

    @Override
    public long count(Long memberId, AbuseSignalType type, LocalDateTime since) {
        Map<String, LocalDateTime> entries = signals.get(new CounterKey(memberId, type));
        if (entries == null) {
            return 0;
        }
        return entries.values().stream()
                .filter(occurredAt -> !occurredAt.isBefore(since))
                .count();
    }

    private record CounterKey(Long memberId, AbuseSignalType type) {
    }
}
