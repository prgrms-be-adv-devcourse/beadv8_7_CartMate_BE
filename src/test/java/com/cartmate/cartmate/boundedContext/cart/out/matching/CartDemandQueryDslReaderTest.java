package com.cartmate.cartmate.boundedContext.cart.out.matching;

import com.cartmate.cartmate.boundedContext.cart.domain.matching.ProductDemand;
import com.cartmate.cartmate.boundedContext.cart.domain.personal.Cart;
import com.cartmate.cartmate.boundedContext.cart.out.personal.CartRepository;
import com.cartmate.cartmate.global.global.QuerydslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({CartDemandQueryDslReader.class, QuerydslConfig.class})
class CartDemandQueryDslReaderTest {

    private static final long MEMBER_BASE = 9_100_000_000L;
    private static final Long POPULAR = 9_100_000_101L;
    private static final Long LESS_POPULAR = 9_100_000_102L;
    private static final Long OLD_ONLY = 9_100_000_103L;
    private static final int ALL = 100_000;

    @Autowired
    private CartDemandReader cartDemandReader;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("기간 안에 담은 서로 다른 회원 수가 최소 인원 이상인 상품만, 담은 회원 집합과 함께 돌려준다")
    void findRecentDemands() {
        addItems(1, POPULAR, LESS_POPULAR);
        addItems(2, POPULAR, LESS_POPULAR);
        addItems(3, POPULAR);
        flushAndClear();

        Map<Long, Set<Long>> demands = demandsOf(cartDemandReader.findRecentDemands(LocalDateTime.now().minusDays(30), 3, ALL));

        assertThat(demands).containsOnlyKeys(POPULAR);
        assertThat(demands.get(POPULAR)).containsExactlyInAnyOrder(member(1), member(2), member(3));
    }

    @Test
    @DisplayName("같은 회원이 같은 상품의 옵션을 여러 개 담아도 1명으로 센다")
    void countDistinctMember() {
        Cart cart = cartRepository.save(Cart.create(member(1)));
        cart.addItem(POPULAR, POPULAR, 1);
        cart.addItem(POPULAR, POPULAR + 1_000, 1);
        addItems(2, POPULAR);
        flushAndClear();

        assertThat(demandsOf(cartDemandReader.findRecentDemands(LocalDateTime.now().minusDays(30), 3, ALL))).isEmpty();
        assertThat(demandsOf(cartDemandReader.findRecentDemands(LocalDateTime.now().minusDays(30), 2, ALL)).get(POPULAR))
                .containsExactlyInAnyOrder(member(1), member(2));
    }

    @Test
    @DisplayName("기간 전에 담고 수정하지 않은 항목은 세지 않는다")
    void excludeOldItems() {
        addItems(1, OLD_ONLY);
        addItems(2, OLD_ONLY);
        flushAndClear();
        em.createNativeQuery("update cart_item set updated_at = :old where product_id = :productId")
                .setParameter("old", LocalDateTime.now().minusDays(31))
                .setParameter("productId", OLD_ONLY)
                .executeUpdate();
        addItems(3, OLD_ONLY);
        flushAndClear();

        Map<Long, Set<Long>> demands = demandsOf(cartDemandReader.findRecentDemands(LocalDateTime.now().minusDays(30), 1, ALL));

        assertThat(demands.get(OLD_ONLY)).containsExactly(member(3));
    }

    @Test
    @DisplayName("기준 시각과 같은 시각에 수정한 항목은 센다")
    void includeSinceBoundary() {
        LocalDateTime since = LocalDateTime.now().minusDays(10).withNano(0);
        addItems(1, OLD_ONLY);
        flushAndClear();
        em.createNativeQuery("update cart_item set updated_at = :since where product_id = :productId")
                .setParameter("since", since)
                .setParameter("productId", OLD_ONLY)
                .executeUpdate();
        flushAndClear();

        assertThat(demandsOf(cartDemandReader.findRecentDemands(since, 1, ALL)).get(OLD_ONLY))
                .containsExactly(member(1));
        assertThat(demandsOf(cartDemandReader.findRecentDemands(since.plusSeconds(1), 1, ALL)))
                .doesNotContainKey(OLD_ONLY);
    }

    @Test
    @DisplayName("담은 회원이 많은 상품부터 조회 개수만큼만 돌려준다")
    void limitByMemberCount() {
        addItems(1, POPULAR, LESS_POPULAR);
        addItems(2, POPULAR, LESS_POPULAR);
        addItems(3, POPULAR);
        flushAndClear();
        LocalDateTime since = LocalDateTime.now().minusDays(30);

        int maxMemberCount = cartDemandReader.findRecentDemands(since, 1, ALL).stream()
                .mapToInt(demand -> demand.memberIds().size())
                .max()
                .orElseThrow();
        List<ProductDemand> top = cartDemandReader.findRecentDemands(since, 1, 1);

        assertThat(top).hasSize(1);
        assertThat(top.getFirst().memberIds()).hasSize(maxMemberCount);
    }

    private void addItems(int memberIndex, Long... productIds) {
        Cart cart = cartRepository.save(Cart.create(member(memberIndex)));
        for (Long productId : productIds) {
            cart.addItem(productId, productId, 1);
        }
    }

    private static Long member(int index) {
        return MEMBER_BASE + index;
    }

    private static Map<Long, Set<Long>> demandsOf(List<ProductDemand> demands) {
        return demands.stream()
                .filter(demand -> demand.productId() >= POPULAR - 100 && demand.productId() <= POPULAR + 10_000)
                .collect(Collectors.toMap(ProductDemand::productId, ProductDemand::memberIds));
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
