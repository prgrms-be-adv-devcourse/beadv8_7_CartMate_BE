package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

import com.cartmate.cartmate.boundedContext.orderService.abuse.exception.AbuseErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;

import java.time.LocalDateTime;

public record AbuseSignalCommand(
        Long memberId,
        AbuseSignalType type,
        String sourceKey,
        LocalDateTime occurredAt,
        String contextKey
) {

    public AbuseSignalCommand(Long memberId, AbuseSignalType type, String sourceKey, LocalDateTime occurredAt) {
        this(memberId, type, sourceKey, occurredAt, null);
    }

    public static String sharedGroupPurchaseContext(Long sharedGroupPurchaseId) {
        return sharedGroupPurchaseId == null ? null : "sharedGroupPurchase:" + sharedGroupPurchaseId;
    }

    public AbuseSignalCommand {
        if (memberId == null || type == null || sourceKey == null || sourceKey.isBlank() || occurredAt == null) {
            throw new ServiceException(AbuseErrorCode.ABUSE_INVALID_SIGNAL);
        }
    }
}
