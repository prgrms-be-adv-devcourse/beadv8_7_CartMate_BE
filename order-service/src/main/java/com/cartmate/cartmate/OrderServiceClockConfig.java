package com.cartmate.cartmate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * order-service가 "지금"을 얻는 시계(TEST-2). Facade · UseCase · 스케줄러는 {@code LocalDateTime.now()} 대신
 * 이 빈을 받아 {@code LocalDateTime.now(clock)}을 쓴다. 테스트는 {@code Clock.fixed(...)}를 넣어 기한 정각 · 1초 전 같은
 * 경계를 정확히 검증한다.
 *
 * <p>시간대는 JVM 기본값을 따른다. Hibernate의 {@code @CreationTimestamp} · {@code @UpdateTimestamp}와 common의 outbox도
 * JVM 시계를 쓰므로, 여기만 다른 시간대로 고정하면 같은 DB에 서로 다른 기준의 시각이 섞인다.
 * 운영 시간대는 JVM 설정({@code TZ})으로 정한다.
 *
 * <p>common이 아니라 order-service에 둔다. common에 두면 이 빈이 다른 서비스 모듈에도 모두 생긴다.
 */
@Configuration(proxyBeanMethods = false)
public class OrderServiceClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
