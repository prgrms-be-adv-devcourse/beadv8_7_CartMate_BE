package com.cartmate.cartmate.global.global;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class RedissonConfig {

    // 분산 락은 키가 지워지면 안 되므로 core Redis에 연결한다. Redisson은 생성 즉시 접속하므로 @Lazy로 첫 사용 시점까지 미룬다.
    @Bean(destroyMethod = "shutdown")
    @Lazy
    public RedissonClient redissonClient(RedisProperties properties) {
        RedisProperties.Node core = properties.core();
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + core.host() + ":" + core.port())
                .setDatabase(properties.database());
        if (properties.password() != null && !properties.password().isBlank()) {
            config.setPassword(properties.password());
        }
        return Redisson.create(config);
    }
}
