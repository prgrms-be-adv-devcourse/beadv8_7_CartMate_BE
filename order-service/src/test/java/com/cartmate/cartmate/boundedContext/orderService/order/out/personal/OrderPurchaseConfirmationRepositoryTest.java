package com.cartmate.cartmate.boundedContext.orderService.order.out.personal;

import com.cartmate.cartmate.support.CartMateJpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@CartMateJpaTest
class OrderPurchaseConfirmationRepositoryTest {

    private static final LocalDateTime FIRST = LocalDateTime.of(2026, 11, 6, 12, 0);

    @Autowired
    private OrderPurchaseConfirmationRepository repository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("같은 주문의 구매확정을 두 번 기록해도 예외 없이 한 건만 남고 먼저 넣은 확정 시각이 유지된다")
    void insertIfAbsentKeepsFirst() {
        Long orderId = 9_400_000_000L + ThreadLocalRandom.current().nextLong(100_000_000L);

        repository.insertIfAbsent(orderId, FIRST);
        repository.insertIfAbsent(orderId, FIRST.plusDays(1));
        em.clear();

        assertThat(repository.findById(orderId)).get()
                .extracting(confirmation -> confirmation.getConfirmedAt())
                .isEqualTo(FIRST);
        assertThat(repository.findAllById(java.util.List.of(orderId))).hasSize(1);
    }
}
