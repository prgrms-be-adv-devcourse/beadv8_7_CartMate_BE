package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.orderService.abuse.exception.AbuseErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLTransientConnectionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AbuseJudgeRunnerTest {

    @Mock
    private AbuseFacade abuseFacade;

    @InjectMocks
    private AbuseJudgeRunner runner;

    @Test
    @DisplayName("판정이 성공하면 실패를 기록하지 않는다")
    void succeeds() {
        assertThat(runner.judge(1L)).isEqualTo(AbuseJudgeOutcome.JUDGED);

        then(abuseFacade).should().judgePending(1L);
        then(abuseFacade).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("다시 해도 같은 실패(예: 규칙·데이터 오류)는 예외 종류와 메시지로 실패를 기록한다")
    void recordsPermanentFailure() {
        willThrow(new IllegalStateException("boom")).given(abuseFacade).judgePending(1L);

        assertThat(runner.judge(1L)).isEqualTo(AbuseJudgeOutcome.FAILURE);

        then(abuseFacade).should().recordJudgeFailure(1L, "IllegalStateException: boom");
    }

    @Test
    @DisplayName("일시 장애(락 대기 · 커넥션 · 트랜잭션 시작 실패)는 실패로 세지 않는다")
    void doesNotCountTransientFailure() {
        willThrow(new CannotAcquireLockException("lock wait timeout")).given(abuseFacade).judgePending(1L);

        assertThat(runner.judge(1L)).isEqualTo(AbuseJudgeOutcome.TRANSIENT_FAILURE);

        then(abuseFacade).should(never()).recordJudgeFailure(anyLong(), anyString());
    }

    @Test
    @DisplayName("일시 장애가 다른 예외에 감싸여 와도 원인을 따라가 일시 장애로 본다")
    void findsTransientCause() {
        willThrow(new IllegalStateException("wrapped", new SQLTransientConnectionException("pool exhausted")))
                .given(abuseFacade).judgePending(1L);

        assertThat(runner.judge(1L)).isEqualTo(AbuseJudgeOutcome.TRANSIENT_FAILURE);
    }

    @Test
    @DisplayName("일시 장애와 영구 실패 분류")
    void classifiesFailures() {
        assertThat(AbuseJudgeRunner.isTransient(new CannotAcquireLockException("lock"))).isTrue();
        assertThat(AbuseJudgeRunner.isTransient(new DataAccessResourceFailureException("db down"))).isTrue();
        assertThat(AbuseJudgeRunner.isTransient(new CannotCreateTransactionException("no connection"))).isTrue();
        assertThat(AbuseJudgeRunner.isTransient(new ServiceException(AbuseErrorCode.ABUSE_MEMBER_STATE_NOT_FOUND))).isFalse();
        assertThat(AbuseJudgeRunner.isTransient(new DataIntegrityViolationException("constraint"))).isFalse();
        assertThat(AbuseJudgeRunner.isTransient(new IllegalStateException("bug"))).isFalse();
    }

    @Test
    @DisplayName("실패 기록마저 실패해도 예외를 던지지 않는다")
    void swallowsRecordFailure() {
        willThrow(new IllegalStateException("boom")).given(abuseFacade).judgePending(1L);
        willThrow(new IllegalStateException("db down")).given(abuseFacade).recordJudgeFailure(anyLong(), anyString());

        assertThat(runner.judge(1L)).isEqualTo(AbuseJudgeOutcome.FAILURE);
    }
}
