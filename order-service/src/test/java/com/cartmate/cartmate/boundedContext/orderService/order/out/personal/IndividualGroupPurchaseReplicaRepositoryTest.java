package com.cartmate.cartmate.boundedContext.orderService.order.out.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.IndividualGroupPurchaseReplica;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.ReplicaPaybackCondition;
import com.cartmate.cartmate.shared.cart.PaybackFunder;
import com.cartmate.cartmate.shared.cart.PaybackOverlapPolicy;
import com.cartmate.cartmate.shared.cart.dto.PaybackConditionPayload;
import com.cartmate.cartmate.support.CartMateJpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@CartMateJpaTest
class IndividualGroupPurchaseReplicaRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);

    @Autowired
    private IndividualGroupPurchaseReplicaRepository repository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("사본의 상품별 페이백 조건이 저장 · 재조회되고, 다시 채워도 (상품, 부담 주체)마다 처음 값만 남는다")
    void paybackConditionsPersistFirstValue() {
        Long purchaseId = 9_500_000_000L + ThreadLocalRandom.current().nextLong(100_000_000L);
        IndividualGroupPurchaseReplica replica = repository.save(IndividualGroupPurchaseReplica.of(
                purchaseId, NOW.plusDays(1), false, null, List.of(1L, 2L), 0));
        replica.fillPaybackConditions(PaybackOverlapPolicy.LARGER, List.of(
                new PaybackConditionPayload(1L, PaybackFunder.ADMIN, null, 10, 500L),
                new PaybackConditionPayload(2L, PaybackFunder.SELLER, 77L, 100, 300L)));
        em.flush();
        em.clear();

        IndividualGroupPurchaseReplica found = repository.findById(purchaseId).orElseThrow();
        found.fillPaybackConditions(PaybackOverlapPolicy.BOTH, List.of(
                new PaybackConditionPayload(1L, PaybackFunder.ADMIN, null, 99, 9L)));
        em.flush();
        em.clear();

        IndividualGroupPurchaseReplica reloaded = repository.findById(purchaseId).orElseThrow();
        assertThat(reloaded.getPaybackOverlapPolicy()).isEqualTo(PaybackOverlapPolicy.LARGER);
        assertThat(reloaded.getPaybackConditions())
                .extracting(ReplicaPaybackCondition::getProductId, ReplicaPaybackCondition::getFunder,
                        ReplicaPaybackCondition::getSellerId, ReplicaPaybackCondition::getMinQuantity,
                        ReplicaPaybackCondition::getAmountPerUnit)
                .containsExactlyInAnyOrder(
                        tuple(1L, PaybackFunder.ADMIN, null, 10, 500L),
                        tuple(2L, PaybackFunder.SELLER, 77L, 100, 300L));
    }
}
