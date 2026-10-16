package com.cartmate.cartmate.boundedContext.cart.out.personal;

import com.cartmate.cartmate.boundedContext.cart.domain.personal.Cart;
import com.cartmate.cartmate.global.global.QuerydslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QuerydslConfig.class)
class CartRepositoryTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("회원 ID로 장바구니를 조회한다")
    void findByMemberId() {
        Cart saved = cartRepository.save(Cart.create(1L));
        flushAndClear();

        Optional<Cart> found = cartRepository.findByMemberId(1L);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("장바구니가 없는 회원이면 빈 Optional을 반환한다")
    void findWithItemsByMemberId_noCart() {
        Optional<Cart> found = cartRepository.findWithItemsByMemberId(999L);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("항목이 없는 장바구니도 조회된다 (left join)")
    void findWithItemsByMemberId_emptyCart() {
        cartRepository.save(Cart.create(1L));
        flushAndClear();

        Optional<Cart> found = cartRepository.findWithItemsByMemberId(1L);

        assertThat(found).isPresent();
        assertThat(found.get().getItems()).isEmpty();
    }

    @Test
    @DisplayName("항목이 여러 개여도 장바구니는 하나로 묶이고, 항목까지 함께 로딩된다 (fetch join)")
    void findWithItemsByMemberId_withItems() {
        Cart cart = cartRepository.save(Cart.create(1L));
        cart.addItem(1L, 1L, 2);
        cart.addItem(1L, 2L, 1);
        flushAndClear();

        Optional<Cart> found = cartRepository.findWithItemsByMemberId(1L);

        assertThat(found).isPresent();
        Cart foundCart = found.get();
        assertThat(em.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(foundCart, "items")).isTrue();
        assertThat(foundCart.getItems()).hasSize(2);
        assertThat(foundCart.getItems())
                .extracting("inventoryId")
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("같은 회원의 장바구니를 두 번 저장하면 유니크 제약에 걸린다")
    void save_duplicateMember() {
        cartRepository.save(Cart.create(1L));
        flushAndClear();

        assertThatThrownBy(() -> cartRepository.saveAndFlush(Cart.create(1L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
