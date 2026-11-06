package com.cartmate.cartmate.boundedContext.orderService.abuse.integration;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseMemberState;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRestriction;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRuleResult;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseMemberStateRepository;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseRestrictionRepository;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseSignalRepository;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@CartMateIntegrationTest
class AbuseJudgeIntegrationTest {

    @Autowired
    private AbuseFacade abuseFacade;

    @Autowired
    private AbuseSignalRepository abuseSignalRepository;

    @Autowired
    private AbuseMemberStateRepository abuseMemberStateRepository;

    @Autowired
    private AbuseRestrictionRepository abuseRestrictionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final long base = 9_300_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @Test
    @DisplayName("30일 안 첫 무산 신호는 1단계 40점으로 판정되고 규칙별 결과가 남는다")
    void firstSignalIsCaution() {
        long member = nextMemberId();

        receiveAndJudge(member, "first", LocalDateTime.now());

        AbuseSignal signal = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member).getFirst();
        assertThat(signal.getStatus()).isEqualTo(AbuseSignalStatus.JUDGED);
        assertThat(signal.getLevel()).isEqualTo(AbuseLevel.CAUTION);
        assertThat(signal.getTotalScore()).isEqualTo(40);
        assertThat(signal.getRuleResults()).containsExactly(
                new AbuseRuleResult("SHARED_ORDER_PAYMENT_EXPIRED", 1, AbuseLevel.CAUTION, 40));
        assertThat(signal.getJudgedAt()).isNotNull();
        AbuseMemberState state = memberState(member);
        assertThat(state.getCurrentLevel()).isEqualTo(AbuseLevel.CAUTION);
        assertThat(state.getRestrictedUntil()).isNull();
        assertThat(abuseRestrictionRepository.findByMemberIdOrderByStartedAtAsc(member)).isEmpty();
    }

    @Test
    @DisplayName("30일 안 세 번째 무산이면 24시간 제한하고, 같은 신호는 다시 판정하지 않으며, 네 번째 무산은 같은 제한을 늘린다")
    void thirdSignalRestrictsAndFourthExtends() {
        long member = nextMemberId();
        LocalDateTime now = LocalDateTime.now();

        receiveAndJudge(member, "first", now.minusDays(20));
        receiveAndJudge(member, "second", now.minusDays(10));
        receiveAndJudge(member, "third", now);

        assertThat(abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member))
                .extracting(AbuseSignal::getLevel)
                .containsExactly(AbuseLevel.CAUTION, AbuseLevel.SUSPICIOUS, AbuseLevel.RESTRICTED);
        AbuseRestriction restriction = onlyRestriction(member);
        AbuseSignal third = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member).getLast();
        assertThat(restriction.getLastSignalId()).isEqualTo(third.getId());
        assertThat(restriction.getEndsAt()).isEqualTo(third.getJudgedAt().plus(AbuseRestriction.RESTRICTION_PERIOD));
        assertThat(memberState(member).getRestrictedUntil()).isEqualTo(restriction.getEndsAt());

        abuseFacade.receive(command(member, "third", now));
        assertThat(abuseFacade.judgePending(member)).isZero();

        receiveAndJudge(member, "fourth", LocalDateTime.now());

        AbuseRestriction extended = onlyRestriction(member);
        AbuseSignal fourth = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member).getLast();
        assertThat(extended.getId()).isEqualTo(restriction.getId());
        assertThat(extended.getExtendedCount()).isEqualTo(1);
        assertThat(extended.getLastSignalId()).isEqualTo(fourth.getId());
        assertThat(extended.getEndsAt()).isEqualTo(fourth.getJudgedAt().plus(AbuseRestriction.RESTRICTION_PERIOD));
        assertThat(memberState(member).getRestrictedUntil()).isEqualTo(extended.getEndsAt());
    }

    @Test
    @DisplayName("제한이 끝난 뒤 다시 3단계가 되면 기존 이력을 늘리지 않고 새 제한을 만든다")
    void newRestrictionAfterPreviousEnded() {
        long member = nextMemberId();
        LocalDateTime now = LocalDateTime.now();
        receiveAndJudge(member, "first", now.minusDays(3));
        receiveAndJudge(member, "second", now.minusDays(2));
        receiveAndJudge(member, "third", now.minusDays(1));
        AbuseRestriction previous = onlyRestriction(member);
        jdbcTemplate.update("UPDATE abuse_member_state SET restricted_until = ? WHERE member_id = ?",
                now.minusMinutes(1), member);

        receiveAndJudge(member, "fourth", LocalDateTime.now());

        List<AbuseRestriction> restrictions = abuseRestrictionRepository.findByMemberIdOrderByStartedAtAsc(member);
        assertThat(restrictions).hasSize(2);
        assertThat(restrictions.getFirst().getId()).isEqualTo(previous.getId());
        assertThat(restrictions.getFirst().getExtendedCount()).isZero();
        AbuseRestriction latest = restrictions.getLast();
        AbuseMemberState state = memberState(member);
        assertThat(state.getActiveRestrictionId()).isEqualTo(latest.getId());
        assertThat(state.getRestrictedUntil()).isEqualTo(latest.getEndsAt());
    }

    @Test
    @DisplayName("30일보다 오래된 신호는 횟수에 들어가지 않는다")
    void signalOutsideWindowIsNotCounted() {
        long member = nextMemberId();

        receiveAndJudge(member, "old", LocalDateTime.now().minusDays(31));

        AbuseSignal signal = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member).getFirst();
        assertThat(signal.getStatus()).isEqualTo(AbuseSignalStatus.JUDGED);
        assertThat(signal.getLevel()).isEqualTo(AbuseLevel.NONE);
        assertThat(signal.getTotalScore()).isZero();
        assertThat(memberState(member).getCurrentLevel()).isEqualTo(AbuseLevel.NONE);
    }

    @Test
    @DisplayName("받은 지 30초 지난 미판정 신호의 회원만 재판정 대상이고, 재판정하면 판정된다")
    void judgesOnlyStaleReceivedSignals() {
        long stale = nextMemberId();
        long fresh = nextMemberId();
        abuseFacade.receive(command(stale, "stale", LocalDateTime.now()));
        abuseFacade.receive(command(fresh, "fresh", LocalDateTime.now()));
        jdbcTemplate.update("UPDATE abuse_signal SET received_at = ? WHERE member_id = ?",
                LocalDateTime.now().minusMinutes(1), stale);

        List<Long> pending = abuseFacade.findPendingMemberIds(10_000);

        assertThat(pending).contains(stale).doesNotContain(fresh);

        assertThat(abuseFacade.judgePending(stale)).isEqualTo(1);

        AbuseSignal signal = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(stale).getFirst();
        assertThat(signal.getStatus()).isEqualTo(AbuseSignalStatus.JUDGED);
        assertThat(signal.getLevel()).isEqualTo(AbuseLevel.CAUTION);
        assertThat(abuseFacade.findPendingMemberIds(10_000)).doesNotContain(stale);
    }

    private void receiveAndJudge(long memberId, String sourceKey, LocalDateTime occurredAt) {
        abuseFacade.receive(command(memberId, sourceKey, occurredAt));
        assertThat(abuseFacade.judgePending(memberId)).isEqualTo(1);
    }

    private AbuseSignalCommand command(long memberId, String sourceKey, LocalDateTime occurredAt) {
        return new AbuseSignalCommand(memberId, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                sourceKey + "-" + memberId, occurredAt);
    }

    private AbuseRestriction onlyRestriction(long memberId) {
        List<AbuseRestriction> restrictions = abuseRestrictionRepository.findByMemberIdOrderByStartedAtAsc(memberId);
        assertThat(restrictions).hasSize(1);
        return restrictions.getFirst();
    }

    private AbuseMemberState memberState(long memberId) {
        return abuseMemberStateRepository.findById(memberId).orElseThrow();
    }

    private long nextMemberId() {
        return base + sequence++;
    }
}
