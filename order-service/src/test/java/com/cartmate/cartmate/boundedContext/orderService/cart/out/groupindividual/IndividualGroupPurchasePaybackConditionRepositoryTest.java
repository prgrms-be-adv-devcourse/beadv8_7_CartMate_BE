package com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchasePaybackCondition;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.PaybackFunder;
import com.cartmate.cartmate.shared.cart.PaybackOverlapPolicy;
import com.cartmate.cartmate.shared.cart.dto.PaybackConditionPayload;
import com.cartmate.cartmate.support.CartMateJpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static com.cartmate.cartmate.boundedContext.orderService.cart.support.GroupPurchaseFixtures.demandPurchase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@CartMateJpaTest
class IndividualGroupPurchasePaybackConditionRepositoryTest {

    private static final Long ADMIN = 9_900_000_001L;
    private static final Long DEMAND_PRODUCT = 9_900_000_101L;
    private static final Long EXTRA_PRODUCT = 9_900_000_102L;
    private static final Long SELLER = 9_900_000_201L;

    @Autowired
    private IndividualGroupPurchaseRepository repository;

    @Autowired
    private EntityManager em;

    private final LocalDateTime now = LocalDateTime.now().withNano(0);

    @Test
    @DisplayName("수요 공동구매를 저장하면 어드민 조건 1건과 겹침 정책이 함께 저장된다")
    void cascadeAdminCondition() {
        Long id = repository.save(demandPurchase(ADMIN, "pb2cascade01", DEMAND_PRODUCT, null, now)).getId();
        flushAndClear();

        IndividualGroupPurchase found = repository.findById(id).orElseThrow();

        assertThat(found.getPaybackOverlapPolicy()).isEqualTo(PaybackOverlapPolicy.BOTH);
        assertThat(found.paybackConditionPayloads())
                .containsExactly(new PaybackConditionPayload(DEMAND_PRODUCT, PaybackFunder.ADMIN, null, 10, 1_000L));
        assertThat(conditionRows(id)).isEqualTo(1L);
    }

    @Test
    @DisplayName("판매자 조건을 더하면 저장되고, 같은 상품에 다시 더하면 false를 돌려주고 행이 늘지 않는다")
    void addSellerCondition() {
        Long id = repository.save(demandPurchase(ADMIN, "pb2seller001", DEMAND_PRODUCT, null, now)).getId();
        flushAndClear();

        IndividualGroupPurchase purchase = repository.findById(id).orElseThrow();
        assertThat(purchase.addSellerPaybackCondition(DEMAND_PRODUCT, SELLER, 5, 300L)).isTrue();
        flushAndClear();

        IndividualGroupPurchase reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.findPaybackCondition(DEMAND_PRODUCT, PaybackFunder.SELLER))
                .hasValueSatisfying(condition -> {
                    assertThat(condition.getSellerId()).isEqualTo(SELLER);
                    assertThat(condition.getMinQuantity()).isEqualTo(5);
                    assertThat(condition.getAmountPerUnit()).isEqualTo(300L);
                    assertThat(condition.getId()).isNotNull();
                    assertThat(condition.getCreatedAt()).isNotNull();
                });
        assertThat(reloaded.addSellerPaybackCondition(DEMAND_PRODUCT, SELLER, 7, 500L)).isFalse();
        flushAndClear();

        assertThat(conditionRows(id)).isEqualTo(2L);
        assertThat(repository.findById(id).orElseThrow()
                .findPaybackCondition(DEMAND_PRODUCT, PaybackFunder.SELLER)
                .map(IndividualGroupPurchasePaybackCondition::getMinQuantity))
                .contains(5);
    }

    @Test
    @DisplayName("상품을 빼도 그 상품의 조건 행은 남고, 같은 상품을 다시 넣어도 유니크 충돌 없이 기존 조건을 쓴다")
    void conditionSurvivesProductRemovalAndReAdd() {
        Long id = repository.save(demandPurchase(ADMIN, "pb2remove001", DEMAND_PRODUCT, null, now)).getId();
        flushAndClear();

        IndividualGroupPurchase purchase = repository.findById(id).orElseThrow();
        purchase.addProduct(ADMIN, EXTRA_PRODUCT, now);
        assertThat(purchase.addSellerPaybackCondition(EXTRA_PRODUCT, SELLER, 5, 300L)).isTrue();
        flushAndClear();

        IndividualGroupPurchase withExtra = repository.findById(id).orElseThrow();
        withExtra.removeProduct(ADMIN, EXTRA_PRODUCT, false, now);
        flushAndClear();

        IndividualGroupPurchase removed = repository.findById(id).orElseThrow();
        assertThat(removed.productIds()).containsExactly(DEMAND_PRODUCT);
        assertThat(removed.findPaybackCondition(EXTRA_PRODUCT, PaybackFunder.SELLER)).isPresent();
        assertThat(conditionRows(id)).isEqualTo(2L);
        assertThatThrownBy(() -> removed.addSellerPaybackCondition(EXTRA_PRODUCT, SELLER, 5, 300L))
                .isInstanceOf(ServiceException.class);

        removed.addProduct(ADMIN, EXTRA_PRODUCT, now);
        assertThat(removed.addSellerPaybackCondition(EXTRA_PRODUCT, SELLER, 9, 900L)).isFalse();
        flushAndClear();

        IndividualGroupPurchase reAdded = repository.findById(id).orElseThrow();
        assertThat(reAdded.productIds()).containsExactlyInAnyOrder(DEMAND_PRODUCT, EXTRA_PRODUCT);
        assertThat(reAdded.findPaybackCondition(EXTRA_PRODUCT, PaybackFunder.SELLER)
                .map(IndividualGroupPurchasePaybackCondition::getAmountPerUnit))
                .contains(300L);
        assertThat(conditionRows(id)).isEqualTo(2L);
    }

    private long conditionRows(Long purchaseId) {
        return ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM individual_group_purchase_payback_condition WHERE individual_group_purchase_id = ?1")
                .setParameter(1, purchaseId)
                .getSingleResult()).longValue();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
