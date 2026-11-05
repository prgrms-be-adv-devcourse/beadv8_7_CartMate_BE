package com.cartmate.cartmate.boundedContext.etcService.abuse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AbuseRestrictionTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 28, 12, 0);

    @Test
    @DisplayName("제한은 시작 시각부터 24시간 뒤에 끝나고 아직 연장되지 않았다")
    void endsAfter24Hours() {
        AbuseRestriction restriction = AbuseRestriction.start(1L, 10L, START);

        assertThat(restriction.getMemberId()).isEqualTo(1L);
        assertThat(restriction.getLastSignalId()).isEqualTo(10L);
        assertThat(restriction.getEndsAt()).isEqualTo(START.plusHours(24));
        assertThat(restriction.getExtendedCount()).isZero();
    }

    @Test
    @DisplayName("다시 3단계 판정을 받으면 그 판정 시각부터 24시간으로 늘리고 연장 횟수를 올린다")
    void extendFromJudgedAt() {
        AbuseRestriction restriction = AbuseRestriction.start(1L, 10L, START);
        LocalDateTime judgedAt = START.plusHours(5);

        boolean extended = restriction.extend(11L, judgedAt);

        assertThat(extended).isTrue();
        assertThat(restriction.getEndsAt()).isEqualTo(judgedAt.plusHours(24));
        assertThat(restriction.getLastSignalId()).isEqualTo(11L);
        assertThat(restriction.getStartedAt()).isEqualTo(START);
        assertThat(restriction.getExtendedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("새 종료 시각이 기존보다 늦지 않으면 바꾸지 않는다")
    void neverShorten() {
        AbuseRestriction restriction = AbuseRestriction.start(1L, 10L, START);

        boolean extended = restriction.extend(11L, START);

        assertThat(extended).isFalse();
        assertThat(restriction.getEndsAt()).isEqualTo(START.plusHours(24));
        assertThat(restriction.getLastSignalId()).isEqualTo(10L);
        assertThat(restriction.getExtendedCount()).isZero();
    }
}
