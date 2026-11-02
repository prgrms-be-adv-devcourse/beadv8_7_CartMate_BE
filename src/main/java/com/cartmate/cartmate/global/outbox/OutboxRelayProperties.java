package com.cartmate.cartmate.global.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 릴레이 설정. yaml에 없으면 기본값을 쓴다 ({@code custom.outbox.relay.*}).
 *
 * @param fixedDelay   폴링 주기 (한 번 끝난 뒤 다음 시작까지). {@code OutboxRelay}의 {@code @Scheduled}가 같은 키를 읽는다
 * @param initialDelay 앱 시작 후 첫 폴링까지
 * @param batchSize    한 번에 읽는 NEW 행 수
 * @param maxAttempts  이 횟수만큼 실패하면 FAILED
 */
@ConfigurationProperties(prefix = "custom.outbox.relay")
public record OutboxRelayProperties(
        @DefaultValue("PT1S") Duration fixedDelay,
        @DefaultValue("PT1S") Duration initialDelay,
        @DefaultValue("100") int batchSize,
        @DefaultValue("10") int maxAttempts
) {
}
