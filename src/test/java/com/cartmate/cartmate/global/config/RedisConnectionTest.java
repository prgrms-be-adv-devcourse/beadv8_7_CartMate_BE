package com.cartmate.cartmate.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = RedisConfig.class)
@EnabledIfEnvironmentVariable(named = "REDIS_PASSWORD", matches = ".+")
public class RedisConnectionTest {

    private static final String KEY = "test:app-check";
    private static final Duration TTL = Duration.ofMinutes(5);

    @Autowired
    @Qualifier("redisCoreConnectionFactory")
    private RedisConnectionFactory coreConnectionFactory;

    @Autowired
    @Qualifier("redisCoreConnectionFactory")
    private RedisConnectionFactory cacheConnectionFactory;

    @Test
    @DisplayName("redis-core에 저장한 값을 다시 읽는다")
    void savesAndReadsFromCore() {
        StringRedisTemplate redis = new StringRedisTemplate(coreConnectionFactory);

        redis.opsForValue().set(KEY, "core-ok", TTL);

        assertThat(redis.opsForValue().get(KEY)).isEqualTo("core-ok");
    }

    @Test
    @DisplayName("redis-cache에 저장한 값을 다시 읽는다")
    void savesAndReadsFromCache() {
        StringRedisTemplate redis = new StringRedisTemplate(cacheConnectionFactory);

        redis.opsForValue().set(KEY, "cache-ok", TTL);

        assertThat(redis.opsForValue().get(KEY)).isEqualTo("cache-ok");
    }

}
