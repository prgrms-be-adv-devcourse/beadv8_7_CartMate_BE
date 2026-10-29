package com.cartmate.cartmate.boundedContext.abuse.in;

import com.cartmate.cartmate.boundedContext.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseSignalRepository;
import com.cartmate.cartmate.boundedContext.order.support.TestDataCleaner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class AbuseJudgeRetryIntegrationTest {

    @Autowired
    private AbuseFacade abuseFacade;

    @Autowired
    private AbuseJudgeRunner abuseJudgeRunner;

    @Autowired
    private AbuseSignalRepository abuseSignalRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> memberIds = new ArrayList<>();

    private final long base = 9_500_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @AfterEach
    void tearDown() {
        new TestDataCleaner(jdbcTemplate).deleteAbuseRecordsOf(memberIds);
    }

    @Test
    @DisplayName("테스트에서 스케줄링을 끄면 재판정 스케줄러 빈이 만들어지지 않는다")
    void schedulerIsDisabledInTests() {
        assertThat(applicationContext.getBeanNamesForType(AbuseJudgeScheduler.class)).isEmpty();
    }

    @Test
    @DisplayName("받은 지 30초 지난 미판정 신호의 회원만 재판정 대상이고, 재판정하면 판정된다")
    void judgesOnlyStaleReceivedSignals() {
        long stale = nextMemberId();
        long fresh = nextMemberId();
        receive(stale, "stale");
        receive(fresh, "fresh");
        jdbcTemplate.update("UPDATE abuse_signal SET received_at = ? WHERE member_id = ?",
                LocalDateTime.now().minusMinutes(1), stale);

        List<Long> pending = abuseFacade.findPendingMemberIds(10_000);

        assertThat(pending).contains(stale).doesNotContain(fresh);

        assertThat(abuseJudgeRunner.judge(stale)).isTrue();

        AbuseSignal signal = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(stale).getFirst();
        assertThat(signal.getStatus()).isEqualTo(AbuseSignalStatus.JUDGED);
        assertThat(signal.getLevel()).isEqualTo(AbuseLevel.CAUTION);
        assertThat(abuseFacade.findPendingMemberIds(10_000)).doesNotContain(stale);
    }

    private void receive(long memberId, String sourceKey) {
        abuseFacade.receive(new AbuseSignalCommand(memberId, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                sourceKey + "-" + memberId, LocalDateTime.now()));
    }

    private long nextMemberId() {
        long memberId = base + sequence++;
        memberIds.add(memberId);
        return memberId;
    }
}
