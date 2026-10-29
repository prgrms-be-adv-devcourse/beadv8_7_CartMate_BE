package com.cartmate.cartmate.boundedContext.abuse.domain;

import com.cartmate.cartmate.boundedContext.abuse.exception.AbuseErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;

import java.time.Duration;

public record AbuseRuleThreshold(
        Duration window,
        int cautionCount,
        int suspiciousCount,
        int restrictedCount,
        int scorePerSignal
) {

    public AbuseRuleThreshold {
        if (window == null || window.isNegative() || window.isZero()
                || cautionCount < 1 || suspiciousCount < cautionCount || restrictedCount < suspiciousCount
                || scorePerSignal < 0) {
            throw new ServiceException(AbuseErrorCode.ABUSE_INVALID_THRESHOLD);
        }
    }

    public AbuseLevel levelOf(long signalCount) {
        if (signalCount >= restrictedCount) {
            return AbuseLevel.RESTRICTED;
        }
        if (signalCount >= suspiciousCount) {
            return AbuseLevel.SUSPICIOUS;
        }
        if (signalCount >= cautionCount) {
            return AbuseLevel.CAUTION;
        }
        return AbuseLevel.NONE;
    }

    public int scoreOf(long signalCount) {
        return Math.toIntExact(signalCount * scorePerSignal);
    }
}
