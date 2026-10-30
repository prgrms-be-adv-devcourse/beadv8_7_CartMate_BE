package com.cartmate.cartmate.global.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
@EnableCaching
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig {

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(10);

    @Bean
    @Primary
    public LettuceConnectionFactory redisCoreConnectionFactory(RedisProperties properties) {
        return connectionFactory(properties.core(), properties);
    }

    @Bean
    public LettuceConnectionFactory redisCacheConnectionFactory(RedisProperties properties) {
        return connectionFactory(properties.cache(), properties);
    }

    @Bean
    public RedisCacheManager cacheManager(
            @Qualifier("redisCacheConnectionFactory") RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_CACHE_TTL);
        return RedisCacheManager.builder(connectionFactory).cacheDefaults(config).build();
    }

    private static LettuceConnectionFactory connectionFactory(RedisProperties.Node node, RedisProperties properties) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(node.host(), node.port());
        config.setDatabase(properties.database());
        if (properties.password() != null && !properties.password().isBlank()) {
            config.setPassword(properties.password());
        }
        return new LettuceConnectionFactory(config);
    }
}
