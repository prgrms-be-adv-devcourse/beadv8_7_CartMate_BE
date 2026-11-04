package com.cartmate.cartmate.boundedContext.member.out;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountVerificationStoreTest {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final AccountVerificationStore store = new AccountVerificationStore(redis);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(ops);
    }

    @Test
    @DisplayName("통과 표시를 회원별 키에 유효시간과 함께 저장한다")
    void saveVerified() {
        store.saveVerified(7L, "hash", Duration.ofMinutes(10));

        verify(ops).set("seller-account:verified:7", "hash", Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("저장된 해시와 같을 때만 확인 완료로 본다")
    void isVerified() {
        when(ops.get("seller-account:verified:7")).thenReturn("hash");

        assertThat(store.isVerified(7L, "hash")).isTrue();
        assertThat(store.isVerified(7L, "other")).isFalse();
    }

    @Test
    @DisplayName("저장된 표시가 없으면 확인 완료가 아니다")
    void isVerifiedWithoutMark() {
        when(ops.get("seller-account:verified:7")).thenReturn(null);

        assertThat(store.isVerified(7L, "hash")).isFalse();
    }

    @Test
    @DisplayName("통과 표시를 지운다")
    void remove() {
        store.remove(7L);

        verify(redis).delete("seller-account:verified:7");
    }

    @Test
    @DisplayName("첫 호출에서 윈도 만료 시간을 건다")
    void firstAttemptSetsExpire() {
        when(ops.increment("seller-account:attempts:7")).thenReturn(1L);

        assertThat(store.tryAcquireAttempt(7L, 5, WINDOW)).isTrue();
        verify(redis).expire("seller-account:attempts:7", WINDOW);
    }

    @Test
    @DisplayName("한도까지는 허용하고 만료 시간을 다시 걸지 않는다")
    void withinLimit() {
        when(ops.increment("seller-account:attempts:7")).thenReturn(5L);

        assertThat(store.tryAcquireAttempt(7L, 5, WINDOW)).isTrue();
        verify(redis, never()).expire("seller-account:attempts:7", WINDOW);
    }

    @Test
    @DisplayName("한도를 넘으면 거절한다")
    void overLimit() {
        when(ops.increment("seller-account:attempts:7")).thenReturn(6L);

        assertThat(store.tryAcquireAttempt(7L, 5, WINDOW)).isFalse();
    }
}
