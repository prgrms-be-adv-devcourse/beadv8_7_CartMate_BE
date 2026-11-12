package com.cartmate.cartmate.boundedContext.paymentService.payout.in.batch;

import com.cartmate.cartmate.boundedContext.paymentService.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.paymentService.payout.out.PayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 정산 지급 배치 1회 요약 로그 (event=payout.batch).
 * 성공 건수 · 지급 금액 합계 · 건너뛴(실패) 건수 · 걸린 시간. 일부 실패는 WARN(다음 실행에서 다시 시도), 배치 실패는 ERROR.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PayoutBatchSummaryListener implements JobExecutionListener {

    private final PayoutRepository payoutRepository;

    @Override
    public void afterJob(JobExecution jobExecution) {
        long paid = 0;
        long skipped = 0;
        for (StepExecution step : jobExecution.getStepExecutions()) {
            paid += step.getWriteCount();
            skipped += step.getSkipCount();
        }
        LocalDateTime start = jobExecution.getStartTime();
        LocalDateTime end = jobExecution.getEndTime() != null ? jobExecution.getEndTime() : LocalDateTime.now();
        long totalAmount = start == null ? 0
                : payoutRepository.sumPayoutAmountPaidBetween(PayoutStatus.COMPLETED, start, end);
        long durationMs = start == null ? 0 : Duration.between(start, end).toMillis();

        String result;
        Level level;
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
            result = "FAILED";
            level = Level.ERROR;
        } else if (skipped > 0) {
            result = "PARTIAL";
            level = Level.WARN;
        } else {
            result = "SUCCESS";
            level = Level.INFO;
        }

        log.atLevel(level).addKeyValue("event", "payout.batch")
                .addKeyValue("count", paid)
                .addKeyValue("skipped", skipped)
                .addKeyValue("totalAmount", totalAmount)
                .addKeyValue("result", result)
                .addKeyValue("durationMs", durationMs)
                .log("정산 지급 배치 {}: 지급 {}건 {}원, 건너뜀 {}건, {}ms", result, paid, totalAmount, skipped, durationMs);
    }
}
