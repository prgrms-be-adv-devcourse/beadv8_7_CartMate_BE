package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

import com.cartmate.cartmate.boundedContext.orderService.abuse.exception.AbuseErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;

import java.time.Duration;

public record AbuseRuleWeight(
        Duration window,
        int restrictAfterCount
) {

    public AbuseRuleWeight {
        if (window == null || window.isNegative() || window.isZero() || restrictAfterCount < 1) {
            throw new ServiceException(AbuseErrorCode.ABUSE_INVALID_THRESHOLD);
        }
    }

    public int scorePerSignal(AbuseScoreThreshold scoreThreshold) {
        return Math.ceilDiv(scoreThreshold.restrictedScore(), restrictAfterCount);
    }

    public int scoreOf(long signalCount, AbuseScoreThreshold scoreThreshold) {
        return Math.toIntExact(signalCount * scorePerSignal(scoreThreshold));
    }
}
