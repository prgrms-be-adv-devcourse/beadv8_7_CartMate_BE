package com.cartmate.cartmate.boundedContext.productService.payback.app;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@TestConfiguration
@Import({PaybackFacade.class, PaybackConditionUseCase.class, GroupPurchaseSnapshotUseCase.class,
        PaybackOrderUseCase.class, PaybackJudgeUseCase.class, PaybackPayoutUseCase.class})
class PaybackAppTestConfig {

    @Bean
    RecordingEventPublisher recordingEventPublisher() {
        return new RecordingEventPublisher();
    }

    static class RecordingEventPublisher implements DomainEventPublisher {

        private final List<DomainEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            events.add(event);
        }

        List<DomainEvent> events() {
            return List.copyOf(events);
        }

        void clear() {
            events.clear();
        }
    }
}
