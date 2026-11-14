package com.cartmate.cartmate.global.global;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * 캐시(Redis) 오류를 기능 오류로 만들지 않는다. 로그만 남기고 넘어가면
 * 조회는 캐시 없이 원래 메서드(DB · 다른 서버 조회)로 진행되고, 저장 · 삭제는 건너뛴다.
 * (스프링 기본 동작은 예외를 그대로 던져 Redis 장애가 곧 조회 실패가 된다)
 */
@Slf4j
public class LoggingCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("캐시 조회 실패, 캐시 없이 진행: cache={}, key={}, error={}", cache.getName(), key, exception.toString());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("캐시 저장 실패, 건너뜀: cache={}, key={}, error={}", cache.getName(), key, exception.toString());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("캐시 삭제 실패, 건너뜀: cache={}, key={}, error={}", cache.getName(), key, exception.toString());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("캐시 비우기 실패, 건너뜀: cache={}, error={}", cache.getName(), exception.toString());
    }
}
