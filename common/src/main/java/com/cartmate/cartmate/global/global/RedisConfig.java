package com.cartmate.cartmate.global.global;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;

@Configuration
@EnableCaching
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig implements CachingConfigurer {

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(10);
    // 캐시 Redis가 응답하지 않을 때 요청이 오래 붙잡히지 않게 짧게 끊는다(기본은 60초). 끊기면 캐시 없이 진행한다
    private static final Duration CACHE_TIMEOUT = Duration.ofMillis(500);

    // Redis 장애가 조회 실패가 되지 않게: 캐시 오류는 로그만 남기고 캐시 없이 진행한다
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    @Bean
    @Primary
    public LettuceConnectionFactory redisCoreConnectionFactory(RedisProperties properties) {
        return connectionFactory(properties.core(), properties);
    }

    // 캐시용 연결만 빨리 실패하게 한다(연결 · 명령 0.5초, 끊겨 있으면 기다리지 않고 바로 거절).
    // 코어 연결(인증 코드 등 캐시가 아닌 저장소)은 그대로 둔다
    @Bean
    public LettuceConnectionFactory redisCacheConnectionFactory(RedisProperties properties) {
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(CACHE_TIMEOUT)
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(CACHE_TIMEOUT).build())
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .build())
                .build();
        return new LettuceConnectionFactory(standaloneConfiguration(properties.cache(), properties), clientConfig);
    }

    // 캐시 값은 JSON으로 저장한다(Serializable 불필요, Redis에서 내용을 바로 볼 수 있음).
    // 꺼낼 때 원래 타입으로 되돌리도록 @class를 함께 저장하되, 우리 코드 · 표준 타입만 허용한다.
    // 서비스별 캐시 TTL · 직렬화는 RedisCacheCustomizer 빈으로 더한다
    @Bean
    public RedisCacheManager cacheManager(
            @Qualifier("redisCacheConnectionFactory") RedisConnectionFactory connectionFactory,
            ObjectProvider<RedisCacheCustomizer> customizers) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_CACHE_TTL)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonValueSerializer()));
        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config);
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }

    static GenericJacksonJsonRedisSerializer jsonValueSerializer() {
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.cartmate.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.lang.")
                .build();
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .enableSpringCacheNullValueSupport()
                .build();
    }

    private static LettuceConnectionFactory connectionFactory(RedisProperties.Node node, RedisProperties properties) {
        return new LettuceConnectionFactory(standaloneConfiguration(node, properties));
    }

    private static RedisStandaloneConfiguration standaloneConfiguration(RedisProperties.Node node, RedisProperties properties) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(node.host(), node.port());
        config.setDatabase(properties.database());
        if (properties.password() != null && !properties.password().isBlank()) {
            config.setPassword(properties.password());
        }
        return config;
    }
}
