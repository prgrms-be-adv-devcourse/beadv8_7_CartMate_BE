package com.cartmate.cartmate.boundedContext.paymentService.payout.in;

import com.cartmate.cartmate.boundedContext.paymentService.payout.in.batch.PayoutJobConfig;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutScheduler {

    static final String LOCK_NAME = "payout.payDue";
    static final String ZONE = "Asia/Seoul";

    private final JobOperator jobOperator;
    private final Job payoutJob;

    // 매일 지급 예정 시각이 지난 대기 정산을 지급하는 배치 Job을 실행한다. 지급 예정일이 매월 15일이라 그날 처음 지급되고, 실패한 건은 다음 날 다시 시도한다.
    // 실행 날짜를 Job 파라미터로 넘겨 같은 날 같은 Job이 중복 실행되지 않게 한다.
    // 실행 시각과 실행 날짜는 서버 시간대가 아니라 한국 시간으로 정한다. 서버가 UTC면 0시 10분이 한국 시간 오전 9시 10분이 되고,
    // 서버마다 시간대가 다르면 실행 날짜가 달라져 같은 날 배치가 두 번 돌 수 있다.
    // 서버가 여러 대면 ShedLock으로 한 대만 시작한다. 같은 순간 여러 대가 시작하면 배치 기록 테이블(BATCH_JOB_INSTANCE)에
    // 행을 넣다가 MySQL 데드락이 나서 한쪽이 실패한다. 락은 배치가 끝날 때까지 유지되고, 서버 시계가 조금 어긋나도
    // 다른 서버가 곧바로 다시 시작하지 않게 최소 1분은 잡아 둔다.
    @Scheduled(cron = "${custom.payout.cron:0 10 0 * * *}", zone = ZONE)
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Observed(name = "batch.payout") // 실행 1회 = span 하나 (스케줄 실행 관찰은 꺼 둠)
    public void payDue() {
        JobParameters parameters = new JobParametersBuilder()
                .addLocalDate("runDate", LocalDate.now(ZoneId.of(ZONE)))
                .toJobParameters();

        try {
            jobOperator.start(payoutJob, parameters);
        } catch (Exception e) {
            // 배치를 시작조차 못한 경우 (시작한 배치의 요약은 PayoutBatchSummaryListener가 남긴다)
            log.atError().addKeyValue("event", "payout.batch").addKeyValue("result", "FAILED")
                    .addKeyValue("error", String.valueOf(e.getMessage())).setCause(e)
                    .log("정산 지급 배치 실행 실패: job={}", PayoutJobConfig.JOB_NAME);
        }
    }
}
