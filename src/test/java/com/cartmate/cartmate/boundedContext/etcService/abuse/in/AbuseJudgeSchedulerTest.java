package com.cartmate.cartmate.boundedContext.etcService.abuse.in;

import com.cartmate.cartmate.boundedContext.etcService.abuse.app.AbuseFacade;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AbuseJudgeSchedulerTest {

    @Mock
    private AbuseFacade abuseFacade;

    @Mock
    private AbuseJudgeRunner abuseJudgeRunner;

    @InjectMocks
    private AbuseJudgeScheduler scheduler;

    @Test
    @DisplayName("재판정 스케줄러는 cart · order 스케줄러와 같은 시간으로 ShedLock을 잡아 한 인스턴스에서만 돈다")
    void runsUnderSchedulerLock() throws NoSuchMethodException {
        SchedulerLock lock = AbuseJudgeScheduler.class.getMethod("judgePendingSignals").getAnnotation(SchedulerLock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.name()).isEqualTo("abuse.judgePending");
        assertThat(lock.lockAtMostFor()).isEqualTo("PT5M");
        assertThat(lock.lockAtLeastFor()).isEqualTo("PT5S");
    }

    @Test
    @DisplayName("미판정 신호가 있는 회원을 배치 크기만큼 골라 회원마다 판정한다")
    void judgesEachPendingMember() {
        given(abuseFacade.findPendingMemberIds(AbuseJudgeScheduler.BATCH_SIZE)).willReturn(List.of(1L, 2L));

        int processed = scheduler.retryPending(Long.MAX_VALUE);

        assertThat(processed).isEqualTo(2);
        then(abuseJudgeRunner).should().judge(1L);
        then(abuseJudgeRunner).should().judge(2L);
    }

    @Test
    @DisplayName("일시 장애가 나면 이번 실행의 남은 회원은 건드리지 않고 멈춘다")
    void stopsOnTransientFailure() {
        given(abuseFacade.findPendingMemberIds(AbuseJudgeScheduler.BATCH_SIZE)).willReturn(List.of(1L, 2L, 3L));
        given(abuseJudgeRunner.judge(1L)).willReturn(AbuseJudgeOutcome.FAILURE);
        given(abuseJudgeRunner.judge(2L)).willReturn(AbuseJudgeOutcome.TRANSIENT_FAILURE);

        int processed = scheduler.retryPending(Long.MAX_VALUE);

        assertThat(processed).isEqualTo(2);
        then(abuseJudgeRunner).should(never()).judge(3L);
    }

    @Test
    @DisplayName("시간 예산이 지나면 남은 회원은 다음 실행으로 넘긴다")
    void stopsAfterTimeBudget() {
        given(abuseFacade.findPendingMemberIds(AbuseJudgeScheduler.BATCH_SIZE)).willReturn(List.of(1L, 2L));

        int processed = scheduler.retryPending(System.nanoTime() - 1);

        assertThat(processed).isZero();
        then(abuseJudgeRunner).should(never()).judge(anyLong());
    }
}
