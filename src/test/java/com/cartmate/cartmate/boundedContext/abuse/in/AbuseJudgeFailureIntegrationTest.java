package com.cartmate.cartmate.boundedContext.abuse.in;

import com.cartmate.cartmate.boundedContext.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseMemberStateRepository;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseSignalRepository;
import com.cartmate.cartmate.boundedContext.abuse.support.AbuseTestDataCleaner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;

@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class AbuseJudgeFailureIntegrationTest {

    @MockitoBean
    private AbuseMemberStateRepository abuseMemberStateRepository;

    @Autowired
    private AbuseFacade abuseFacade;

    @Autowired
    private AbuseJudgeRunner abuseJudgeRunner;

    @Autowired
    private AbuseSignalRepository abuseSignalRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> memberIds = new ArrayList<>();

    private final long base = 9_600_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @AfterEach
    void tearDown() {
        new AbuseTestDataCleaner(jdbcTemplate).deleteRecordsOf(memberIds);
    }

    @Test
    @DisplayName("판정이 실패하면 판정은 롤백돼도 실패 기록은 남고, 최대 시도 횟수째 실패에서 FAILED가 되어 재판정 대상에서 빠진다")
    void recordsFailureUntilFailed() {
        long member = nextMemberId();
        abuseFacade.receive(new AbuseSignalCommand(member, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                "fail-" + member, LocalDateTime.now()));

        assertThat(abuseJudgeRunner.judge(member)).isEqualTo(AbuseJudgeOutcome.FAILURE);

        AbuseSignal afterFirst = onlySignal(member);
        assertThat(afterFirst.getStatus()).isEqualTo(AbuseSignalStatus.RECEIVED);
        assertThat(afterFirst.getAttemptCount()).isEqualTo(1);
        assertThat(afterFirst.getLastError()).startsWith("ServiceException");
        assertThat(afterFirst.getJudgedAt()).isNull();

        for (int i = 1; i < AbuseSignal.MAX_JUDGE_ATTEMPTS; i++) {
            abuseJudgeRunner.judge(member);
        }

        AbuseSignal failed = onlySignal(member);
        assertThat(failed.getStatus()).isEqualTo(AbuseSignalStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(AbuseSignal.MAX_JUDGE_ATTEMPTS);
        jdbcTemplate.update("UPDATE abuse_signal SET received_at = ? WHERE member_id = ?",
                LocalDateTime.now().minusMinutes(1), member);
        assertThat(abuseFacade.findPendingMemberIds(10_000)).doesNotContain(member);
    }

    @Test
    @DisplayName("실패 기록은 RECEIVED 신호에만 남고 이미 판정된 신호는 건드리지 않는다")
    void failureDoesNotTouchJudgedSignals() {
        long member = nextMemberId();
        abuseFacade.receive(new AbuseSignalCommand(member, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                "judged-" + member, LocalDateTime.now().minusMinutes(1)));
        jdbcTemplate.update("UPDATE abuse_signal SET status = 'JUDGED', level = 'CAUTION', judged_at = ? WHERE member_id = ?",
                LocalDateTime.now(), member);
        abuseFacade.receive(new AbuseSignalCommand(member, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                "pending-" + member, LocalDateTime.now()));

        abuseJudgeRunner.judge(member);

        List<AbuseSignal> signals = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member);
        assertThat(signals.getFirst().getStatus()).isEqualTo(AbuseSignalStatus.JUDGED);
        assertThat(signals.getFirst().getAttemptCount()).isZero();
        assertThat(signals.getFirst().getLastError()).isNull();
        assertThat(signals.getLast().getStatus()).isEqualTo(AbuseSignalStatus.RECEIVED);
        assertThat(signals.getLast().getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("일시 장애로 판정이 실패하면 실패로 세지 않고 신호는 RECEIVED로 남아 다음 실행에서 재판정된다")
    void transientFailureIsNotCounted() {
        long member = nextMemberId();
        abuseFacade.receive(new AbuseSignalCommand(member, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                "transient-" + member, LocalDateTime.now().minusMinutes(1)));
        willThrow(new CannotAcquireLockException("lock wait timeout"))
                .given(abuseMemberStateRepository).insertIfAbsent(anyLong(), any());

        assertThat(abuseJudgeRunner.judge(member)).isEqualTo(AbuseJudgeOutcome.TRANSIENT_FAILURE);

        AbuseSignal signal = onlySignal(member);
        assertThat(signal.getStatus()).isEqualTo(AbuseSignalStatus.RECEIVED);
        assertThat(signal.getAttemptCount()).isZero();
        assertThat(signal.getLastError()).isNull();
        jdbcTemplate.update("UPDATE abuse_signal SET received_at = ? WHERE member_id = ?",
                LocalDateTime.now().minusMinutes(1), member);
        assertThat(abuseFacade.findPendingMemberIds(10_000)).contains(member);
    }

    private AbuseSignal onlySignal(long memberId) {
        List<AbuseSignal> signals = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(memberId);
        assertThat(signals).hasSize(1);
        return signals.getFirst();
    }

    private long nextMemberId() {
        long memberId = base + sequence++;
        memberIds.add(memberId);
        return memberId;
    }
}
