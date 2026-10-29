package com.cartmate.cartmate.boundedContext.abuse.domain;

import com.cartmate.cartmate.boundedContext.abuse.exception.AbuseErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;

public record AbuseScoreThreshold(
        int cautionScore,
        int suspiciousScore,
        int restrictedScore
) {

    public AbuseScoreThreshold {
        if (cautionScore < 1 || suspiciousScore < cautionScore || restrictedScore < suspiciousScore) {
            throw new ServiceException(AbuseErrorCode.ABUSE_INVALID_THRESHOLD);
        }
    }

    public AbuseLevel levelOf(int totalScore) {
        if (totalScore >= restrictedScore) {
            return AbuseLevel.RESTRICTED;
        }
        if (totalScore >= suspiciousScore) {
            return AbuseLevel.SUSPICIOUS;
        }
        if (totalScore >= cautionScore) {
            return AbuseLevel.CAUTION;
        }
        return AbuseLevel.NONE;
    }
}
