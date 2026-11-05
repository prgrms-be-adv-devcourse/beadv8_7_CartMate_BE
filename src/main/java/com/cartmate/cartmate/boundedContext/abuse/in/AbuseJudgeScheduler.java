package com.cartmate.cartmate.boundedContext.abuse.in;

import com.cartmate.cartmate.boundedContext.abuse.app.AbuseFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cartmate.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class AbuseJudgeScheduler {

    static final int BATCH_SIZE = 100;
    static final Duration TIME_BUDGET = Duration.ofSeconds(10);
    static final String LOCK_NAME = "abuse.judgePending";
    static final String LOCK_AT_MOST = "PT5M";
    static final String LOCK_AT_LEAST = "PT5S";
    private static final long INTERVAL_MILLIS = 60_000L;

    private final AbuseFacade abuseFacade;
    private final AbuseJudgeRunner abuseJudgeRunner;

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = LOCK_AT_MOST, lockAtLeastFor = LOCK_AT_LEAST)
    public void judgePendingSignals() {
        retryPending(System.nanoTime() + TIME_BUDGET.toNanos());
    }

    int retryPending(long deadlineNanos) {
        List<Long> memberIds = abuseFacade.findPendingMemberIds(BATCH_SIZE);
        int processed = 0;
        for (Long memberId : memberIds) {
            if (System.nanoTime() > deadlineNanos) {
                log.warn("어뷰징 재판정 시간 예산 초과, 남은 회원은 다음 실행에서 처리: processed={}, total={}",
                        processed, memberIds.size());
                break;
            }
            AbuseJudgeOutcome outcome = abuseJudgeRunner.judge(memberId);
            processed++;
            if (outcome == AbuseJudgeOutcome.TRANSIENT_FAILURE) {
                log.warn("일시 장애로 어뷰징 재판정 중단, 남은 회원은 다음 실행에서 처리: processed={}, total={}",
                        processed, memberIds.size());
                break;
            }
        }
        return processed;
    }
}
