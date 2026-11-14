package com.cartmate.cartmate.boundedContext.orderService.abuse.integration;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.shared.abuse.dto.AbuseRestrictionStatus;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@CartMateIntegrationTest
class AbuseRestrictionQueryIntegrationTest {

    @Autowired
    private AbuseFacade abuseFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final long base = 9_700_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @Test
    @DisplayName("제한 종료 시각 전이면 제한 중이고 종료 시각을 돌려주며, 종료 시각이 지났거나 회원 상태가 없으면 제한이 아니다")
    void restrictedOnlyBeforeEndsAt() {
        long restricted = nextMemberId();
        long expired = nextMemberId();
        long unknown = nextMemberId();
        LocalDateTime until = LocalDateTime.now().plusHours(3).truncatedTo(ChronoUnit.SECONDS);
        saveState(restricted, until);
        saveState(expired, LocalDateTime.now().minusMinutes(1));

        assertThat(abuseFacade.getRestriction(restricted)).isEqualTo(AbuseRestrictionStatus.restrictedUntil(restricted, until));
        assertThat(abuseFacade.getRestriction(expired)).isEqualTo(AbuseRestrictionStatus.none(expired));
        assertThat(abuseFacade.getRestriction(unknown)).isEqualTo(AbuseRestrictionStatus.none(unknown));
    }

    @Test
    @DisplayName("제한 조회는 호출한 쪽 트랜잭션에 합류하지 않아, 그 트랜잭션의 커밋 안 된 변경을 보지 않는다")
    void doesNotJoinCallerTransaction() {
        long member = nextMemberId();

        AbuseRestrictionStatus seenInsideCaller = transactionTemplate.execute(status -> {
            saveState(member, LocalDateTime.now().plusHours(1));
            status.setRollbackOnly();
            return abuseFacade.getRestriction(member);
        });

        assertThat(seenInsideCaller).isEqualTo(AbuseRestrictionStatus.none(member));
    }

    private void saveState(long memberId, LocalDateTime restrictedUntil) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO abuse_member_state (member_id, current_level, restricted_until, created_at, updated_at)
                VALUES (?, 'RESTRICTED', ?, ?, ?)
                """, memberId, restrictedUntil, now, now);
    }

    private long nextMemberId() {
        return base + sequence++;
    }
}
