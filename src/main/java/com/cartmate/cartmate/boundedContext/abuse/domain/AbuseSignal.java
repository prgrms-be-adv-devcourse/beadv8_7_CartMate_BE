package com.cartmate.cartmate.boundedContext.abuse.domain;

import com.cartmate.cartmate.boundedContext.abuse.exception.AbuseErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;

import java.time.LocalDateTime;

public record AbuseSignal(
        Long memberId,
        AbuseSignalType type,
        String sourceKey,
        LocalDateTime occurredAt
) {

    public AbuseSignal {
        if (memberId == null || type == null || sourceKey == null || sourceKey.isBlank() || occurredAt == null) {
            throw new ServiceException(AbuseErrorCode.ABUSE_INVALID_SIGNAL);
        }
    }
}
