package com.cartmate.cartmate.boundedContext.abuse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AbuseMemberStateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

    @Test
    @DisplayName("처음 만든 회원 상태는 NONE이고 제한 중이 아니다")
    void createdAsNone() {
        AbuseMemberState state = AbuseMemberState.create(1L, NOW);

        assertThat(state.getCurrentLevel()).isEqualTo(AbuseLevel.NONE);
        assertThat(state.isRestricted(NOW)).isFalse();
        assertThat(state.activeRestrictionId(NOW)).isEmpty();
    }

    @Test
    @DisplayName("판정을 반영하면 현재 단계와 마지막 판정 시각이 바뀐다")
    void appliesJudgment() {
        AbuseMemberState state = AbuseMemberState.create(1L, NOW);

        state.applyJudgment(AbuseLevel.SUSPICIOUS, NOW.plusMinutes(1));

        assertThat(state.getCurrentLevel()).isEqualTo(AbuseLevel.SUSPICIOUS);
        assertThat(state.getLastJudgedAt()).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    @DisplayName("제한 종료 시각 전까지만 제한 중이고 진행 중인 제한 ID를 돌려준다")
    void restrictedUntilEndsAt() {
        AbuseMemberState state = AbuseMemberState.create(1L, NOW);
        LocalDateTime endsAt = NOW.plusHours(24);

        state.restrict(7L, endsAt, NOW);

        assertThat(state.getRestrictedUntil()).isEqualTo(endsAt);
        assertThat(state.isRestricted(endsAt.minusSeconds(1))).isTrue();
        assertThat(state.activeRestrictionId(endsAt.minusSeconds(1))).contains(7L);
        assertThat(state.isRestricted(endsAt)).isFalse();
        assertThat(state.activeRestrictionId(endsAt)).isEmpty();
    }
}
