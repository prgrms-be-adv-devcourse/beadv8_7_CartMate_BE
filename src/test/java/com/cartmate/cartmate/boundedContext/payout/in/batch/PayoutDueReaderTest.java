package com.cartmate.cartmate.boundedContext.payout.in.batch;

import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayoutDueReaderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 0, 10);
    private static final PageRequest PAGE = PageRequest.of(0, 2);

    private final PayoutRepository payoutRepository = mock(PayoutRepository.class);
    private final PayoutDueReader reader = new PayoutDueReader(payoutRepository, NOW, 2);

    private void due(long lastId, List<Long> ids) {
        when(payoutRepository.findDueIdsAfter(PayoutStatus.PENDING, NOW, lastId, PAGE)).thenReturn(ids);
    }

    @Test
    @DisplayName("마지막으로 읽은 ID 다음부터 페이지 단위로 끝까지 읽고 null을 반환한다")
    void readsAllPages() {
        due(0L, List.of(1L, 2L));
        due(2L, List.of(3L));

        assertThat(reader.read()).isEqualTo(1L);
        assertThat(reader.read()).isEqualTo(2L);
        assertThat(reader.read()).isEqualTo(3L);
        assertThat(reader.read()).isNull();
        assertThat(reader.read()).isNull();

        verify(payoutRepository, times(1)).findDueIdsAfter(PayoutStatus.PENDING, NOW, 0L, PAGE);
        verify(payoutRepository, times(1)).findDueIdsAfter(PayoutStatus.PENDING, NOW, 2L, PAGE);
    }

    @Test
    @DisplayName("대상이 페이지 크기의 배수이면 빈 페이지를 한 번 더 조회한 뒤 끝낸다")
    void exactMultipleOfPageSize() {
        due(0L, List.of(1L, 2L));
        due(2L, List.of());

        assertThat(reader.read()).isEqualTo(1L);
        assertThat(reader.read()).isEqualTo(2L);
        assertThat(reader.read()).isNull();
        assertThat(reader.read()).isNull();

        verify(payoutRepository, times(1)).findDueIdsAfter(PayoutStatus.PENDING, NOW, 2L, PAGE);
    }

    @Test
    @DisplayName("대상이 없으면 한 번만 조회하고 null을 반환한다")
    void empty() {
        due(0L, List.of());

        assertThat(reader.read()).isNull();
        assertThat(reader.read()).isNull();

        verify(payoutRepository, times(1)).findDueIdsAfter(PayoutStatus.PENDING, NOW, 0L, PAGE);
    }
}
