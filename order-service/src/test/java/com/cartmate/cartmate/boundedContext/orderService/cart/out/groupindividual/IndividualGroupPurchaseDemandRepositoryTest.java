package com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.support.CartMateJpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@CartMateJpaTest
class IndividualGroupPurchaseDemandRepositoryTest {

    private static final Long ADMIN = 9_200_000_001L;
    private static final Long OPEN_PRODUCT = 9_200_000_101L;
    private static final Long CLOSED_PRODUCT = 9_200_000_102L;
    private static final Long EXPIRED_PRODUCT = 9_200_000_103L;
    private static final Long OLD_CLOSED_PRODUCT = 9_200_000_104L;
    private static final List<Long> TEST_PRODUCTS =
            List.of(OPEN_PRODUCT, CLOSED_PRODUCT, EXPIRED_PRODUCT, OLD_CLOSED_PRODUCT);

    @Autowired
    private IndividualGroupPurchaseRepository repository;

    @Autowired
    private EntityManager em;

    private final LocalDateTime now = LocalDateTime.now().withNano(0);

    @Test
    @DisplayName("판매 중인 수요 공동구매만 상품별 ID로 조회하고, 키로 잠금 조회할 수 있다")
    void openDemandPurchases() {
        IndividualGroupPurchase open = repository.save(open(OPEN_PRODUCT, "mat9open0001", now.minusHours(1)));
        repository.save(open(EXPIRED_PRODUCT, "mat9expired1", now.minusDays(4)));
        flushAndClear();

        assertThat(repository.findOpenDemandPurchaseIds(TEST_PRODUCTS, now))
                .containsExactlyEntriesOf(java.util.Map.of(OPEN_PRODUCT, open.getId()));
        assertThat(repository.findByDemandProductKeyForUpdate(OPEN_PRODUCT))
                .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(open.getId()));
        assertThat(repository.findOpenDemandPurchaseIds(List.of(), now)).isEmpty();
    }

    @Test
    @DisplayName("재개설 제한 기간 안에 닫혔거나 마감 시각이 지난 수요 공동구매의 상품만 돌려준다")
    void endedDemandProducts() {
        repository.save(open(OPEN_PRODUCT, "mat9open0001", now.minusHours(1)));
        IndividualGroupPurchase closed = repository.save(open(CLOSED_PRODUCT, "mat9closed01", now.minusDays(2)));
        closed.close(ADMIN, now.minusDays(1));
        repository.save(open(EXPIRED_PRODUCT, "mat9expired1", now.minusDays(4)));
        IndividualGroupPurchase oldClosed = repository.save(open(OLD_CLOSED_PRODUCT, "mat9oldclose", now.minusDays(30)));
        oldClosed.close(ADMIN, now.minusDays(29));
        flushAndClear();

        List<Long> ended = repository.findDemandProductIdsEndedBetween(now.minusDays(14), now).stream()
                .filter(TEST_PRODUCTS::contains)
                .toList();

        assertThat(ended).containsExactlyInAnyOrder(CLOSED_PRODUCT, EXPIRED_PRODUCT);
    }

    @Test
    @DisplayName("DB 최후 방어: 같은 상품의 키가 잡혀 있으면 유니크 제약에 걸린다")
    void uniqueDemandKey() {
        repository.saveAndFlush(open(OPEN_PRODUCT, "mat9first001", now.minusHours(1)));

        assertThatThrownBy(() -> repository.saveAndFlush(open(OPEN_PRODUCT, "mat9second01", now.minusHours(1))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_individual_group_purchase_demand_product");
    }

    @Test
    @DisplayName("닫아서 키를 비우면 같은 상품으로 다시 열 수 있다")
    void reopenAfterClose() {
        IndividualGroupPurchase first = repository.saveAndFlush(open(OPEN_PRODUCT, "mat9first001", now.minusHours(1)));
        first.close(ADMIN, now);
        repository.saveAndFlush(first);

        IndividualGroupPurchase reopened = repository.saveAndFlush(open(OPEN_PRODUCT, "mat9third001", now));

        assertThat(reopened.getDemandProductKey()).isEqualTo(OPEN_PRODUCT);
    }

    @Test
    @DisplayName("마감 시각이 지난 이전 공동구매의 키를 풀면 같은 상품으로 다시 열 수 있다")
    void reopenAfterExpired() {
        repository.saveAndFlush(open(OPEN_PRODUCT, "mat9expired1", now.minusDays(4)));
        flushAndClear();

        IndividualGroupPurchase expired = repository.findByDemandProductKeyForUpdate(OPEN_PRODUCT).orElseThrow();
        expired.releaseDemandProductKey(now);
        repository.flush();
        IndividualGroupPurchase reopened = repository.saveAndFlush(open(OPEN_PRODUCT, "mat9reopen01", now));

        assertThat(reopened.getDemandProductKey()).isEqualTo(OPEN_PRODUCT);
        assertThat(repository.findOpenDemandPurchaseIds(List.of(OPEN_PRODUCT), now))
                .containsEntry(OPEN_PRODUCT, reopened.getId());
    }

    @Test
    @DisplayName("판매 중인 어드민 공동구매만 마감 임박 순으로 조회하고, 회원 공동구매 · 마감 지난 것 · 닫힌 것은 뺀다")
    void findOpenDemandPurchases() {
        IndividualGroupPurchase later = repository.save(IndividualGroupPurchase.openFromDemand(
                "수요 공동구매", ADMIN, "mat11later01", OPEN_PRODUCT, now.plusDays(3), 10, 1_000L, now));
        IndividualGroupPurchase sooner = repository.save(IndividualGroupPurchase.openFromDemand(
                "수요 공동구매", ADMIN, "mat11soon001", CLOSED_PRODUCT, now.plusDays(1), 10, 1_000L, now));
        repository.save(open(EXPIRED_PRODUCT, "mat11expired", now.minusDays(4)));
        IndividualGroupPurchase closed = repository.save(open(OLD_CLOSED_PRODUCT, "mat11closed1", now.minusHours(1)));
        closed.close(ADMIN, now);
        repository.save(IndividualGroupPurchase.create("회원 공동구매", ADMIN, "mat11member1", List.of(OPEN_PRODUCT), now));
        flushAndClear();

        List<Long> ids = repository.findOpenDemandPurchases(now).stream()
                .filter(purchase -> TEST_PRODUCTS.contains(purchase.getSourceProductId())
                        || ADMIN.equals(purchase.getCreatorMemberId()))
                .map(IndividualGroupPurchase::getId)
                .toList();

        assertThat(ids).containsExactly(sooner.getId(), later.getId());
    }

    private static IndividualGroupPurchase open(Long productId, String inviteCode, LocalDateTime openedAt) {
        return IndividualGroupPurchase.openFromDemand(
                "수요 공동구매", ADMIN, inviteCode, productId, null, 10, 1_000L, openedAt);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
