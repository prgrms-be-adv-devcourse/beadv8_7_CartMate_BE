package com.cartmate.cartmate.boundedContext.abuse.integration;

import com.cartmate.cartmate.boundedContext.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseMemberStateRepository;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseRestrictionRepository;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseSignalRepository;
import com.cartmate.cartmate.boundedContext.abuse.support.AbuseTestDataCleaner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class AbuseJudgeConcurrencyTest {

    private static final int THREADS = 8;

    @Autowired
    private AbuseFacade abuseFacade;

    @Autowired
    private AbuseSignalRepository abuseSignalRepository;

    @Autowired
    private AbuseMemberStateRepository abuseMemberStateRepository;

    @Autowired
    private AbuseRestrictionRepository abuseRestrictionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> memberIds = new ArrayList<>();

    private final long base = 9_400_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @AfterEach
    void tearDown() {
        new AbuseTestDataCleaner(jdbcTemplate).deleteRecordsOf(memberIds);
    }

    @Test
    @DisplayName("회원 상태 행이 없는 회원을 여러 스레드가 동시에 판정해도 교착 없이 신호마다 한 번만 판정하고 제한은 한 행만 생긴다")
    void concurrentJudgeOfNewMember() throws InterruptedException {
        long member = nextMemberId();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 3; i++) {
            abuseFacade.receive(new AbuseSignalCommand(member, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                    "concurrent-" + i, now.minusMinutes(i)));
        }

        AtomicInteger judged = new AtomicInteger();
        Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
        runConcurrently(() -> {
            try {
                judged.addAndGet(abuseFacade.judgePending(member));
            } catch (Throwable e) {
                errors.add(e);
            }
        });

        assertThat(errors).isEmpty();
        assertThat(judged.get()).isEqualTo(3);
        assertThat(abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member))
                .extracting(AbuseSignal::getStatus)
                .containsOnly(AbuseSignalStatus.JUDGED);
        assertThat(abuseRestrictionRepository.findByMemberIdOrderByStartedAtAsc(member)).hasSize(1);
        assertThat(abuseMemberStateRepository.findById(member).orElseThrow().getCurrentLevel())
                .isEqualTo(AbuseLevel.RESTRICTED);
    }

    @Test
    @DisplayName("회원 락을 기다리는 사이 커밋된 신호도 락을 잡은 뒤 시각 기준으로 자기 자신까지 센다")
    void signalCommittedWhileWaitingForLockCountsItself() throws Exception {
        long member = nextMemberId();
        abuseFacade.receive(new AbuseSignalCommand(member, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                "old", LocalDateTime.now().minusDays(31)));
        abuseFacade.judgePending(member);

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            abuseMemberStateRepository.findForUpdate(member);
            locked.countDown();
            awaitQuietly(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        Future<Integer> judging = executor.submit(() -> abuseFacade.judgePending(member));
        Thread.sleep(300);

        abuseFacade.receive(new AbuseSignalCommand(member, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                "late", LocalDateTime.now()));
        release.countDown();

        holder.get(10, TimeUnit.SECONDS);
        assertThat(judging.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        executor.shutdown();
        AbuseSignal late = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member).getLast();
        assertThat(late.getSourceKey()).isEqualTo("late");
        assertThat(late.getLevel()).isEqualTo(AbuseLevel.CAUTION);
        assertThat(late.getTotalScore()).isEqualTo(40);
    }

    private void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void runConcurrently(Runnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        for (int i = 0; i < THREADS; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
    }

    private long nextMemberId() {
        long memberId = base + sequence++;
        memberIds.add(memberId);
        return memberId;
    }
}
