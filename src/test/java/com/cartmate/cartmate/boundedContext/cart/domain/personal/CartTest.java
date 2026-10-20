package com.cartmate.cartmate.boundedContext.cart.domain.personal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class CartTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long BALL_PRODUCT = 100L;
    private static final Long BALL_SKU = 10L;
    private static final Long PIN_PRODUCT = 200L;
    private static final Long PIN_SKU = 20L;

    @Test
    @DisplayName("주문 수량이 담긴 수량보다 적으면 남은 수량으로 항목을 유지한다")
    void partialDecreaseKeepsRemaining() {
        Cart cart = Cart.create(MEMBER_ID);
        cart.addItem(BALL_PRODUCT, BALL_SKU, 3);

        cart.removeOrderedQuantities(Map.of(BALL_SKU, 2));

        assertThat(cart.getItems())
                .extracting(CartItem::getInventoryId, CartItem::getQuantity)
                .containsExactly(tuple(BALL_SKU, 1));
    }

    @Test
    @DisplayName("주문 수량이 담긴 수량과 같거나 많으면 항목을 지운다")
    void exactOrOverQuantityRemovesItem() {
        Cart cart = Cart.create(MEMBER_ID);
        cart.addItem(BALL_PRODUCT, BALL_SKU, 2);
        cart.addItem(PIN_PRODUCT, PIN_SKU, 1);

        cart.removeOrderedQuantities(Map.of(BALL_SKU, 2, PIN_SKU, 5));

        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("장바구니에 없는 상품 옵션은 무시한다")
    void ignoreMissingInventory() {
        Cart cart = Cart.create(MEMBER_ID);
        cart.addItem(BALL_PRODUCT, BALL_SKU, 2);

        cart.removeOrderedQuantities(Map.of(PIN_SKU, 1));

        assertThat(cart.getItems())
                .extracting(CartItem::getInventoryId, CartItem::getQuantity)
                .containsExactly(tuple(BALL_SKU, 2));
    }
}
