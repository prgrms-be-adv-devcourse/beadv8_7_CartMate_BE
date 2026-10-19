package com.cartmate.cartmate.boundedContext.payout.in.batch;

import com.cartmate.cartmate.boundedContext.payout.app.PayoutFacade;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;

@Configuration
public class PayoutJobConfig {

    public static final String JOB_NAME = "payoutJob";

    private static final int READ_PAGE_SIZE = 100;

    @Bean
    public Job payoutJob(JobRepository jobRepository, Step payoutStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(payoutStep)
                .build();
    }

    // 정산 한 건씩 별도 트랜잭션으로 지급을 요청한다. 한 건이 실패해도 건너뛰고 나머지를 계속 처리하며, 실패한 정산은 대기 상태로 남아 다음 실행 때 다시 대상이 된다.
    @Bean
    public Step payoutStep(
            JobRepository jobRepository, PlatformTransactionManager transactionManager,
            ItemReader<Long> payoutDueReader, ItemWriter<Long> payoutWriter, PayoutSkipListener skipListener
    ) {
        return new StepBuilder("payoutStep", jobRepository)
                .<Long, Long>chunk(1)
                .transactionManager(transactionManager)
                .reader(payoutDueReader)
                .writer(payoutWriter)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(Long.MAX_VALUE)
                .skipListener(skipListener)
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<Long> payoutDueReader(PayoutRepository payoutRepository) {
        return new PayoutDueReader(payoutRepository, LocalDateTime.now(), READ_PAGE_SIZE);
    }

    @Bean
    public ItemWriter<Long> payoutWriter(PayoutFacade payoutFacade) {
        return chunk -> chunk.getItems().forEach(payoutFacade::pay);
    }
}
