package com.cartmate.cartmate.boundedContext.etcService.shared.abuse.dto;

import java.time.LocalDateTime;

public record AbuseRestrictionStatus(
        Long memberId,
        boolean restricted,
        LocalDateTime restrictedUntil
) {

    public AbuseRestrictionStatus {
        if (restricted && restrictedUntil == null) {
            throw new IllegalArgumentException("제한 중이면 제한 종료 시각이 있어야 합니다.");
        }
    }

    public static AbuseRestrictionStatus none(Long memberId) {
        return new AbuseRestrictionStatus(memberId, false, null);
    }

    public static AbuseRestrictionStatus restrictedUntil(Long memberId, LocalDateTime restrictedUntil) {
        return new AbuseRestrictionStatus(memberId, true, restrictedUntil);
    }
}
