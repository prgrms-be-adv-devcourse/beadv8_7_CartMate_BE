package com.cartmate.cartmate.boundedContext.abuse.out;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;

import java.time.Duration;
import java.time.LocalDateTime;

public interface AbuseSignalCounter {

    boolean record(AbuseSignalCommand signal, Duration retention);

    long count(Long memberId, AbuseSignalType type, LocalDateTime since);
}
