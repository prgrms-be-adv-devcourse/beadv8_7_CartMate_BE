package com.cartmate.cartmate.boundedContext.member.out;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 Redis에 붙어서 Lua 스크립트와 키 만료를 확인한다. REDIS_CORE_HOST가 있을 때만 실행한다.
// 호스트는 REDIS_CORE_HOST, 포트는 REDIS_CORE_PORT(기본 6380), DB는 REDIS_DATABASE(기본 1)를 쓴다.
@EnabledIfEnvironmentVariable(named = "REDIS_CORE_HOST", matches = ".+")
class AccountVerificationStoreRedisTest {

    private static final Long MEMBER_ID = 987_654_321L; // 실제 회원 ID와 겹치지 않는 값
    private static final String KEY = "seller-account:attempts:" + MEMBER_ID;
    private static final Duration WINDOW = Duration.ofSeconds(60);

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private AccountVerificationStore store;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                System.getenv("REDIS_CORE_HOST"),
                Integer.parseInt(System.getenv().getOrDefault("REDIS_CORE_PORT", "6380")));
        config.setDatabase(Integer.parseInt(System.getenv().getOrDefault("REDIS_DATABASE", "1")));
        String password = System.getenv("REDIS_PASSWORD");
        if (password != null && !password.isBlank()) {
            config.setPassword(password);
        }
        factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        store = new AccountVerificationStore(redis);
        redis.delete(KEY);
    }

    @AfterEach
    void tearDown() {
        redis.delete(KEY);
        factory.destroy();
    }

    @Test
    @DisplayName("첫 호출에서 만료 시간이 걸리고, 이후 호출은 만료 시간을 다시 걸지 않는다")
    void expireIsSetOnceAtomically() {
        assertThat(store.tryAcquireAttempt(MEMBER_ID, 5, WINDOW)).isTrue();
        Long firstTtl = redis.getExpire(KEY);
        assertThat(firstTtl).isBetween(1L, 60L); // -1(만료 없음)이 아니다

        store.tryAcquireAttempt(MEMBER_ID, 5, WINDOW);
        assertThat(redis.getExpire(KEY)).isLessThanOrEqualTo(firstTtl); // 윈도가 늘어나지 않는다
        assertThat(redis.opsForValue().get(KEY)).isEqualTo("2");
    }

    @Test
    @DisplayName("한도까지는 허용하고 넘으면 거절한다")
    void limit() {
        for (int i = 0; i < 5; i++) {
            assertThat(store.tryAcquireAttempt(MEMBER_ID, 5, WINDOW)).isTrue();
        }
        assertThat(store.tryAcquireAttempt(MEMBER_ID, 5, WINDOW)).isFalse();
        assertThat(redis.getExpire(KEY)).isPositive();
    }
}
