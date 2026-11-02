package com.cartmate.cartmate.boundedContext.abuse.in;

import com.cartmate.cartmate.boundedContext.abuse.app.AbuseFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class AbuseJudgeRunnerTest {

    @Mock
    private AbuseFacade abuseFacade;

    @InjectMocks
    private AbuseJudgeRunner runner;

    @Test
    @DisplayName("판정이 성공하면 실패를 기록하지 않는다")
    void succeeds() {
        assertThat(runner.judge(1L)).isTrue();

        then(abuseFacade).should().judgePending(1L);
        then(abuseFacade).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("판정이 실패하면 예외 종류와 메시지로 실패를 기록한다")
    void recordsFailure() {
        willThrow(new IllegalStateException("boom")).given(abuseFacade).judgePending(1L);

        assertThat(runner.judge(1L)).isFalse();

        then(abuseFacade).should().recordJudgeFailure(1L, "IllegalStateException: boom");
    }

    @Test
    @DisplayName("실패 기록마저 실패해도 예외를 던지지 않는다")
    void swallowsRecordFailure() {
        willThrow(new IllegalStateException("boom")).given(abuseFacade).judgePending(1L);
        willThrow(new IllegalStateException("db down")).given(abuseFacade).recordJudgeFailure(anyLong(), anyString());

        assertThat(runner.judge(1L)).isFalse();
    }
}
