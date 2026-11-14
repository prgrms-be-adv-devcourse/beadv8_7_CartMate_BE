package com.cartmate.cartmate.global.global;

import org.springframework.data.redis.cache.RedisCacheManager;

/**
 * 서비스별 캐시 설정(캐시 이름마다 TTL · 직렬화)을 공통 캐시 매니저에 더한다.
 * 이 타입의 빈을 등록하면 RedisConfig가 캐시 매니저를 만들 때 적용한다.
 * 예) builder.withCacheConfiguration("salesPostDetailInventory", config.entryTtl(Duration.ofSeconds(2)))
 */
@FunctionalInterface
public interface RedisCacheCustomizer {

    void customize(RedisCacheManager.RedisCacheManagerBuilder builder);
}
