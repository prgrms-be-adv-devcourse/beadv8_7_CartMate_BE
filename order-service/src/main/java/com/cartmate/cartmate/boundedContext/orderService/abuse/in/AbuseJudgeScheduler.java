package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cartmate.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AbuseJudgeProperties.class)
public class AbuseJudgeScheduler {

    static final String LOCK_NAME = "abuse.judgePending";
    static final String LOCK_AT_MOST = "PT5M";
    static final String LOCK_AT_LEAST = "PT5S";
    private static final long INTERVAL_MILLIS = 60_000L;

    private final AbuseFacade abuseFacade;
    private final AbuseJudgeRunner abuseJudgeRunner;
    private final AbuseJudgeProperties properties;

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = LOCK_AT_MOST, lockAtLeastFor = LOCK_AT_LEAST)
    public void judgePendingSignals() {
        retryPending(System.nanoTime() + properties.timeBudget().toNanos());
    }

    int retryPending(long deadlineNanos) {
        List<Long> memberIds = abuseFacade.findPendingMemberIds(properties.batchSize());
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
