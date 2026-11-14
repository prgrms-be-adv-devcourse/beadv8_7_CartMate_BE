package com.cartmate.cartmate.boundedContext.productService.config;

import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostDetailInventoryLoader;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SalesPostDetailInventories;
import com.cartmate.cartmate.global.global.RedisCacheCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

// 상품 서비스 캐시 설정. 공통 기본값(10분, JSON)과 다른 캐시만 여기서 정한다
@Configuration
public class ProductServiceCacheConfig {

    // 판매글 상세의 선택 목록 · 재고: 몰릴 때만 막는 용도라 아주 짧게. 수정 · 삭제 뒤 최대 2초 예전 값이 보일 수 있다
    static final Duration DETAIL_INVENTORY_TTL = Duration.ofSeconds(2);

    @Bean
    public RedisCacheCustomizer productServiceCacheCustomizer() {
        return builder -> builder.withCacheConfiguration(SalesPostDetailInventoryLoader.CACHE_NAME,
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(DETAIL_INVENTORY_TTL)
                        // 값 타입이 정해져 있어 타입 정보(@class) 없이 그 타입으로 읽는다
                        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                                new JacksonJsonRedisSerializer<>(SalesPostDetailInventories.class))));
    }
}
