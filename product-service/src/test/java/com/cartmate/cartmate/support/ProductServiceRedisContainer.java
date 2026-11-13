package com.cartmate.cartmate.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 캐시를 쓰는 테스트용 Redis(한 번 띄워 여러 테스트가 같이 쓴다).
 * 캐시가 필요한 테스트만 @DynamicPropertySource에서 register를 부른다. 나머지 테스트는 Redis 없이 돈다.
 * 공통 RedisConfig는 custom.redis.* 설정을 읽으므로(cache · core 노드) 둘 다 이 컨테이너를 가리키게 한다.
 */
public final class ProductServiceRedisContainer {

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    static {
        REDIS.start();
    }

    private ProductServiceRedisContainer() {
    }

    // 컨테이너를 지금 띄운다(클래스 로딩). 테스트가 웹 서버 포트를 고르기 전에 불러야
    // Docker가 그 포트를 가져가 "이미 사용 중"으로 서버가 못 뜨는 일이 없다
    public static void start() {
        // static 블록에서 이미 시작된다
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("custom.redis.cache.host", REDIS::getHost);
        registry.add("custom.redis.cache.port", () -> REDIS.getMappedPort(6379));
        registry.add("custom.redis.core.host", REDIS::getHost);
        registry.add("custom.redis.core.port", () -> REDIS.getMappedPort(6379));
        registry.add("custom.redis.password", () -> "");
    }
}
