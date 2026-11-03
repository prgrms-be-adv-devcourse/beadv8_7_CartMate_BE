package com.cartmate.cartmate.global.lock;

import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributedLockExecutorTest {

    private static final String KEY = "lock:k";

    private final RedissonClient redisson = mock(RedissonClient.class);
    private final RLock rLock = mock(RLock.class);
    private final DistributedLockExecutor executor = new DistributedLockExecutor(redisson);

    @BeforeEach
    void setUp() {
        when(redisson.getLock(KEY)).thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    @DisplayName("락을 얻으면 action을 실행하고 결과를 돌려준 뒤 락을 푼다")
    void runsAndUnlocks() throws Throwable {
        when(rLock.tryLock(3, TimeUnit.SECONDS)).thenReturn(true);

        String result = executor.call(KEY, 3, () -> "done");

        assertThat(result).isEqualTo("done");
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("action이 예외를 던져도 락을 풀고 예외를 그대로 던진다")
    void unlocksWhenActionFails() throws Exception {
        when(rLock.tryLock(3, TimeUnit.SECONDS)).thenReturn(true);
        IllegalStateException failure = new IllegalStateException("boom");

        assertThatThrownBy(() -> executor.call(KEY, 3, () -> {
            throw failure;
        })).isSameAs(failure);

        verify(rLock).unlock();
    }

    @Test
    @DisplayName("락을 못 얻으면 LOCK_ACQUISITION_FAILED를 던지고 action을 실행하지 않는다")
    void failsWhenNotAcquired() throws Exception {
        when(rLock.tryLock(3, TimeUnit.SECONDS)).thenReturn(false);
        AtomicBoolean ran = new AtomicBoolean();

        assertThatThrownBy(() -> executor.call(KEY, 3, () -> ran.getAndSet(true)))
                .isInstanceOfSatisfying(ServiceException.class, e ->
                        assertThat(e.getResultCode()).isEqualTo(LockErrorCode.LOCK_ACQUISITION_FAILED.resultCode()));

        assertThat(ran).isFalse();
        verify(rLock, never()).unlock();
    }

    @Test
    @DisplayName("락을 기다리다 중단되면 LOCK_ACQUISITION_FAILED를 던지고 중단 표시를 남긴다")
    void restoresInterruptFlagWhenInterrupted() throws Exception {
        when(rLock.tryLock(3, TimeUnit.SECONDS)).thenThrow(new InterruptedException());

        try {
            assertThatThrownBy(() -> executor.call(KEY, 3, () -> "x"))
                    .isInstanceOfSatisfying(ServiceException.class, e ->
                            assertThat(e.getResultCode()).isEqualTo(LockErrorCode.LOCK_ACQUISITION_FAILED.resultCode()));

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        verify(rLock, never()).unlock();
    }

    @Test
    @DisplayName("action 중에 락이 만료되어 내 락이 아니면 unlock을 호출하지 않는다")
    void doesNotUnlockWhenNoLongerHeld() throws Throwable {
        when(rLock.tryLock(3, TimeUnit.SECONDS)).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        executor.call(KEY, 3, () -> "done");

        verify(rLock, never()).unlock();
    }

    @Test
    @DisplayName("run은 키 앞에 lock:을 붙이고 기본 3초 동안 기다려 action을 실행한다")
    void runPrefixesKeyAndUsesDefaultWait() throws Exception {
        when(rLock.tryLock(3, TimeUnit.SECONDS)).thenReturn(true);
        AtomicBoolean ran = new AtomicBoolean();

        executor.run("k", () -> ran.set(true));

        assertThat(ran).isTrue();
        verify(redisson).getLock(KEY);
        verify(rLock).tryLock(3, TimeUnit.SECONDS);
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("run은 action의 런타임 예외를 감싸지 않고 그대로 던지며 락을 푼다")
    void runRethrowsRuntimeException() throws Exception {
        when(rLock.tryLock(3, TimeUnit.SECONDS)).thenReturn(true);
        IllegalStateException failure = new IllegalStateException("boom");

        assertThatThrownBy(() -> executor.run("k", () -> {
            throw failure;
        })).isSameAs(failure);

        verify(rLock).unlock();
    }

    @Test
    @DisplayName("run도 락을 못 얻으면 LOCK_ACQUISITION_FAILED를 던지고 action을 실행하지 않는다")
    void runFailsWhenNotAcquired() throws Exception {
        when(rLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);
        AtomicBoolean ran = new AtomicBoolean();

        assertThatThrownBy(() -> executor.run("k", () -> ran.set(true)))
                .isInstanceOfSatisfying(ServiceException.class, e ->
                        assertThat(e.getResultCode()).isEqualTo(LockErrorCode.LOCK_ACQUISITION_FAILED.resultCode()));

        assertThat(ran).isFalse();
    }
}
