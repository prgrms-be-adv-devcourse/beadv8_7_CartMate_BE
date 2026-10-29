package com.cartmate.cartmate.boundedContext.abuse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AbuseRestrictionTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 28, 12, 0);

    @Test
    @DisplayName("제한은 시작 시각부터 24시간 동안 유효하고 그 뒤에는 자동으로 풀린다")
    void activeFor24Hours() {
        AbuseRestriction restriction = AbuseRestriction.start(1L, 10L, START);

        assertThat(restriction.getEndsAt()).isEqualTo(START.plusHours(24));
        assertThat(restriction.isActive(START)).isTrue();
        assertThat(restriction.isActive(START.plusHours(24).minusSeconds(1))).isTrue();
        assertThat(restriction.isActive(START.plusHours(24))).isFalse();
    }

    @Test
    @DisplayName("다시 3단계 판정을 받으면 그 판정 시각부터 24시간으로 늘린다")
    void extendFromJudgedAt() {
        AbuseRestriction restriction = AbuseRestriction.start(1L, 10L, START);
        LocalDateTime judgedAt = START.plusHours(5);

        boolean extended = restriction.extend(11L, judgedAt);

        assertThat(extended).isTrue();
        assertThat(restriction.getEndsAt()).isEqualTo(judgedAt.plusHours(24));
        assertThat(restriction.getJudgmentId()).isEqualTo(11L);
        assertThat(restriction.getStartedAt()).isEqualTo(START);
    }

    @Test
    @DisplayName("새 종료 시각이 기존보다 이르면 줄이지 않는다")
    void neverShorten() {
        AbuseRestriction restriction = AbuseRestriction.start(1L, 10L, START);

        boolean extended = restriction.extend(11L, START.minusHours(1));

        assertThat(extended).isFalse();
        assertThat(restriction.getEndsAt()).isEqualTo(START.plusHours(24));
        assertThat(restriction.getJudgmentId()).isEqualTo(10L);
    }
}
