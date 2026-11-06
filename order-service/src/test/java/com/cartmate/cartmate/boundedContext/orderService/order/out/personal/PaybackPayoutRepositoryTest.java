package com.cartmate.cartmate.boundedContext.orderService.order.out.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.PaybackSettlement;
import com.cartmate.cartmate.shared.order.GroupPurchaseType;
import com.cartmate.cartmate.shared.cart.PaybackFunder;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.PaybackPayout;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.PaybackPayoutStatus;
import com.cartmate.cartmate.support.CartMateJpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@CartMateJpaTest
class PaybackPayoutRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 13, 0);

    @Autowired
    private PaybackPayoutRepository repository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("상태별로 재요청 대기 id를 오래된 순으로 읽는다")
    void findIdsByStatus() {
        Long purchaseId = 9_500_000_000L + ThreadLocalRandom.current().nextLong(100_000_000L);
        PaybackPayout first = repository.save(PaybackPayout.request(GroupPurchaseType.INDIVIDUAL, purchaseId, adminLine(1L, 1_000L), NOW));
        PaybackPayout second = repository.save(PaybackPayout.request(GroupPurchaseType.INDIVIDUAL, purchaseId, adminLine(2L, 1_000L), NOW));
        repository.save(PaybackPayout.request(GroupPurchaseType.INDIVIDUAL, purchaseId, adminLine(3L, 1_000L), NOW));
        first.markFailed("WALLET_BUSY", true, first.getAttempt(), NOW);
        second.markFailed("WALLET_BUSY", true, second.getAttempt(), NOW);
        em.flush();
        em.clear();

        assertThat(repository.findIdsByStatus(PaybackPayoutStatus.RETRY_WAITING, Limit.of(10)))
                .containsSubsequence(first.getId(), second.getId())
                .doesNotContainNull();
        assertThat(repository.findIdsByStatus(PaybackPayoutStatus.RETRY_WAITING, Limit.of(1))).hasSize(1);
        assertThat(repository.findByIdForUpdate(first.getId())).get()
                .extracting(PaybackPayout::getStatus).isEqualTo(PaybackPayoutStatus.RETRY_WAITING);
    }

    @Test
    @DisplayName("같은 공동구매의 같은 구매자에게는 지급 행이 하나뿐이다")
    void uniquePerBuyer() {
        Long purchaseId = 9_600_000_000L + ThreadLocalRandom.current().nextLong(100_000_000L);
        repository.saveAndFlush(PaybackPayout.request(GroupPurchaseType.INDIVIDUAL, purchaseId, adminLine(1L, 1_000L), NOW));

        assertThatThrownBy(() -> repository.saveAndFlush(PaybackPayout.request(GroupPurchaseType.INDIVIDUAL, purchaseId, adminLine(1L, 1_000L), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static PaybackSettlement.PayoutLine adminLine(Long memberId, long amount) {
        return new PaybackSettlement.PayoutLine(memberId, 1L, PaybackFunder.ADMIN, null, 1, amount);
    }
}
