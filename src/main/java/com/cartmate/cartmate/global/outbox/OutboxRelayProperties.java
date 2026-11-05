package com.cartmate.cartmate.global.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 릴레이 설정. yaml에 없으면 기본값을 쓴다 ({@code custom.outbox.relay.*}).
 *
 * @param fixedDelay     폴링 주기 (한 번 끝난 뒤 다음 시작까지). {@code OutboxRelay}의 {@code @Scheduled}가 같은 키를 읽는다
 * @param initialDelay   앱 시작 후 첫 폴링까지
 * @param batchSize      한 번에 읽는 NEW 행 수
 * @param maxAttempts    이 횟수만큼 실패하면 FAILED
 * @param initialBackoff 첫 실패 뒤 다시 시도할 때까지. 실패할 때마다 두 배
 * @param maxBackoff     재시도 간격 상한
 */
@ConfigurationProperties(prefix = "custom.outbox.relay")
public record OutboxRelayProperties(
        @DefaultValue("PT1S") Duration fixedDelay,
        @DefaultValue("PT1S") Duration initialDelay,
        @DefaultValue("100") int batchSize,
        @DefaultValue("15") int maxAttempts,
        @DefaultValue("PT1S") Duration initialBackoff,
        @DefaultValue("PT10M") Duration maxBackoff
) {

    private static final int MAX_SHIFT = 30;

    /**
     * {@code attempts}번째 실패 뒤 다음 시도까지 기다릴 시간. initialBackoff × 2^(attempts-1), maxBackoff로 자른다.
     * 기본값(1초 · 10분 · 15회)이면 마지막 시도까지 약 57분을 버틴다.
     */
    public Duration backoffAfter(int attempts) {
        if (attempts < 1) {
            return Duration.ZERO;
        }
        int shift = Math.min(attempts - 1, MAX_SHIFT);
        Duration delay = initialBackoff.multipliedBy(1L << shift);
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }
}
