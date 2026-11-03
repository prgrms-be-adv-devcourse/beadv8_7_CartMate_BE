package com.cartmate.cartmate.global.outbox;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 스케줄러를 끈 통합 테스트(cartmate.scheduling.enabled=false)에서 아웃박스 릴레이만 직접 돌리고 싶을 때 쓴다.
 * 스케줄링 전체를 켜면 다른 스케줄러까지 캐시된 컨텍스트에서 계속 돌아 다른 테스트 데이터를 건드린다.
 * 자동 폴링은 함께 쓰는 {@code custom.outbox.relay.initial-delay=PT1H}로 미루고 테스트가 {@link OutboxRelay#relay()}를 부른다.
 */
@TestConfiguration
public class ManualOutboxRelayConfig {

    @Bean
    OutboxRelay outboxRelay(OutboxEventRepository outboxEventRepository, OutboxRelaySink sink,
                            OutboxRelayWorker worker, OutboxRelayProperties properties) {
        return new OutboxRelay(outboxEventRepository, sink, worker, properties);
    }
}
