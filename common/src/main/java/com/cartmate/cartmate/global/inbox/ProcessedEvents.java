package com.cartmate.cartmate.global.inbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 받는 쪽의 중복 처리 방지. 처리 로직과 <b>같은 트랜잭션</b>에서 먼저 부른다(MANDATORY).
 * <pre>
 * if (!processedEvents.markFirst("cart.order-paid", eventId)) return; // 이미 처리함
 * ... 처리 ...
 * </pre>
 * 처리가 실패해 롤백되면 기록도 함께 사라져 다음 전달에서 다시 처리한다. 처리가 커밋되면 기록도 남아 다음 전달은 건너뛴다.
 *
 * <p>INSERT IGNORE를 쓴다. 엔티티 저장 후 unique 위반을 잡는 방식은 Hibernate 세션과 트랜잭션을 망가뜨려(롤백 전용)
 * "이미 처리함"을 정상 흐름으로 다룰 수 없다. 동시에 같은 이벤트가 두 번 와도 unique 행 락으로 하나만 1을 받는다.
 */
@Component
@RequiredArgsConstructor
public class ProcessedEvents {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 이 받는 쪽에서 처음 보는 이벤트면 기록하고 true, 이미 처리했으면 false.
     *
     * @param consumer 받는 쪽 이름(모듈.이벤트 처럼 고정 문자열, 100자 이내)
     * @param eventId  봉투의 eventId(UUID)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markFirst(String consumer, String eventId) {
        int inserted = jdbcTemplate.update(
                "INSERT IGNORE INTO processed_event (consumer, event_id, processed_at) VALUES (?, ?, ?)",
                consumer, eventId, LocalDateTime.now());
        return inserted == 1;
    }
}
