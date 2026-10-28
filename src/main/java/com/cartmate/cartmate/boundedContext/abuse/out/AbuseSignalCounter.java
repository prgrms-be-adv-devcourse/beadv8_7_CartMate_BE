package com.cartmate.cartmate.boundedContext.abuse.out;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;

import java.time.Duration;
import java.time.LocalDateTime;

public interface AbuseSignalCounter {

    boolean record(AbuseSignal signal, Duration retention);

    long count(Long memberId, AbuseSignalType type, LocalDateTime since);
}
