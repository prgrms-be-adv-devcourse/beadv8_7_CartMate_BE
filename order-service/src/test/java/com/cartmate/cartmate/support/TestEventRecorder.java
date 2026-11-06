package com.cartmate.cartmate.support;

import com.cartmate.cartmate.global.event.DomainEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

/**
 * 커밋된 도메인 이벤트를 모두 기록한다(ARCH-18). 테스트마다 따로 두던 이벤트 기록기(@Import로 넣으면 컨텍스트가 갈라짐)를 하나로 합쳤다.
 * {@code ManualOutboxRelayConfig}가 가져오므로 {@link CartMateIntegrationTest}를 단 테스트는 주입만 받으면 된다.
 * 컨텍스트를 여러 테스트가 같이 쓰므로 테스트 시작 때 {@link #clear()}하고, 셀 때는 자기 데이터의 ID로 거른다.
 */
@TestConfiguration
public class TestEventRecorder {

    private final Queue<DomainEvent> events = new ConcurrentLinkedQueue<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(DomainEvent event) {
        events.add(event);
    }

    public <E extends DomainEvent> long count(Class<E> type, Predicate<E> filter) {
        return events.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(filter)
                .count();
    }

    public void clear() {
        events.clear();
    }
}
