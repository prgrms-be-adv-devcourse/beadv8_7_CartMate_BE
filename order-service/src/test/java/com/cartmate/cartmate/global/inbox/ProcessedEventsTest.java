package com.cartmate.cartmate.global.inbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessedEventsTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ProcessedEvents processedEvents = new ProcessedEvents(jdbcTemplate);

    @Test
    @DisplayName("같은 소비자 · eventId의 첫 기록은 true, 중복 키로 막힌 두 번째 기록은 false다")
    void firstTrueThenFalse() {
        when(jdbcTemplate.update(anyString(), any(), any(), any()))
                .thenReturn(1)
                .thenThrow(new DuplicateKeyException("uk_processed_event_consumer_event"));

        assertThat(processedEvents.markFirst("cart.order-paid", "event-1")).isTrue();
        assertThat(processedEvents.markFirst("cart.order-paid", "event-1")).isFalse();
    }

    @Test
    @DisplayName("중복 키가 아닌 무결성 오류(값 잘림 등)는 이미 처리한 것으로 삼키지 않고 던진다")
    void otherIntegrityErrorPropagates() {
        when(jdbcTemplate.update(anyString(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("Data too long for column 'consumer'"));

        assertThatThrownBy(() -> processedEvents.markFirst("x".repeat(200), "event-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
