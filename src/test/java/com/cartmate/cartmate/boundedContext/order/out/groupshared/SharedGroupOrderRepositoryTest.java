package com.cartmate.cartmate.boundedContext.order.out.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SharedGroupOrderRepositoryTest {

    private static final LocalDateTime CLOSED_AT = LocalDateTime.of(2026, 10, 19, 12, 0);

    @Autowired
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    @Autowired
    private EntityManager em;

    private final long base = 8_500_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private final Long creator = base;

    private final Long member = base + 1;

    @Test
    @DisplayName("상세 조회는 참여자와 항목을 함께 불러온다")
    void findWithDetailsById() {
        SharedGroupOrder saved = sharedGroupOrderRepository.save(order(base + 10));
        flushAndClear();

        SharedGroupOrder found = sharedGroupOrderRepository.findWithDetailsById(saved.getId()).orElseThrow();
        flushAndClear();

        assertThat(found.getParticipants()).hasSize(2);
        assertThat(found.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("없는 주문이면 상세 조회 결과가 비어 있다")
    void findWithDetailsByIdEmpty() {
        assertThat(sharedGroupOrderRepository.findWithDetailsById(999_999L)).isEmpty();
    }

    @Test
    @DisplayName("참여 중인 공동 주문만 최신순으로 조회하고 항목도 함께 불러온다")
    void findAllByParticipant() {
        SharedGroupOrder first = sharedGroupOrderRepository.save(order(base + 10));
        SharedGroupOrder second = sharedGroupOrderRepository.save(order(base + 11));
        sharedGroupOrderRepository.save(SharedGroupOrder.create(
                base + 12, base + 2, List.of(base + 2), List.of(new OrderItemLine(10L, 1L, null, "상품", null, 1, 1000)), CLOSED_AT));
        flushAndClear();

        List<SharedGroupOrder> found = sharedGroupOrderRepository.findAllByParticipant(member);
        flushAndClear();

        assertThat(found).extracting(SharedGroupOrder::getId).containsExactly(second.getId(), first.getId());
        assertThat(found).allSatisfy(order -> assertThat(order.getItems()).hasSize(2));
    }

    @Test
    @DisplayName("결제 기한 + 유예 시간이 지난 결제 중 주문만 취소 대상이다")
    void findIdsToCancelByDeadline() {
        SharedGroupOrder saved = sharedGroupOrderRepository.save(order(base + 10));
        flushAndClear();
        LocalDateTime cancelAt = saved.getPaymentDeadlineAt().plus(SharedGroupOrder.GRACE_PERIOD);

        assertThat(sharedGroupOrderRepository.findIdsToCancelByDeadline(cancelAt.minusSeconds(1))).doesNotContain(saved.getId());
        assertThat(sharedGroupOrderRepository.findIdsToCancelByDeadline(cancelAt)).contains(saved.getId());
    }

    @Test
    @DisplayName("같은 공동구매로 공동 주문을 두 번 만들 수 없다")
    void uniqueSharedGroupPurchase() {
        sharedGroupOrderRepository.save(order(base + 10));
        flushAndClear();

        assertThat(sharedGroupOrderRepository.existsBySharedGroupPurchaseId(base + 10)).isTrue();
        assertThatThrownBy(() -> {
            sharedGroupOrderRepository.save(order(base + 10));
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private SharedGroupOrder order(Long sharedGroupPurchaseId) {
        return SharedGroupOrder.create(
                sharedGroupPurchaseId,
                creator,
                List.of(creator, member),
                List.of(new OrderItemLine(10L, 1L, null, "상품", null, 2, 25000), new OrderItemLine(11L, 2L, null, "상품", null, 1, 15000)),
                CLOSED_AT);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
