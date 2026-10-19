package com.cartmate.cartmate.boundedContext.payout.in;

import com.cartmate.cartmate.boundedContext.payout.in.batch.PayoutJobConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutScheduler {

    private final JobOperator jobOperator;
    private final Job payoutJob;

    // 매일 지급 예정 시각이 지난 대기 정산을 지급하는 배치 Job을 실행한다. 지급 예정일이 매월 15일이라 그날 처음 지급되고, 실패한 건은 다음 날 다시 시도한다.
    // 실행 날짜를 Job 파라미터로 넘겨 같은 날 같은 Job이 중복 실행되지 않게 한다.
    @Scheduled(cron = "${custom.payout.cron:0 10 0 * * *}")
    public void payDue() {
        JobParameters parameters = new JobParametersBuilder()
                .addLocalDate("runDate", LocalDate.now())
                .toJobParameters();

        try {
            jobOperator.start(payoutJob, parameters);
        } catch (Exception e) {
            log.error("정산 지급 배치 실행 실패: job={}", PayoutJobConfig.JOB_NAME, e);
        }
    }
}
