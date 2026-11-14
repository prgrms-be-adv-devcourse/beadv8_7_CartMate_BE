package com.cartmate.cartmate.global.observability;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    // Spring이 @Scheduled 실행마다 만드는 관찰 이름
    static final String SCHEDULED_TASK_OBSERVATION = "tasks.scheduled.execution";

    @Bean
    public TraceparentSupport traceparentSupport(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        return new TraceparentSupport(tracer.getIfAvailable(() -> Tracer.NOOP),
                propagator.getIfAvailable(() -> Propagator.NOOP));
    }

    /**
     * 스케줄 실행 관찰은 전부 끈다. 1초 · 몇 초 간격 폴러가 일이 없어도 매번 trace를 만들어 Tempo가 빈 trace로 차는 것을 막는다.
     * 대신 Outbox 릴레이 · Inbox 처리는 행마다({@link TraceparentSupport}), cron 배치는 실행마다({@code @Observed}) span을 직접 연다.
     */
    @Bean
    public ObservationPredicate noScheduledTaskObservation() {
        return (name, context) -> !SCHEDULED_TASK_OBSERVATION.equals(name);
    }

    @Bean
    public SentryEventFilter sentryEventFilter(ObjectProvider<Tracer> tracer) {
        return new SentryEventFilter(tracer.getIfAvailable(() -> Tracer.NOOP));
    }
}
