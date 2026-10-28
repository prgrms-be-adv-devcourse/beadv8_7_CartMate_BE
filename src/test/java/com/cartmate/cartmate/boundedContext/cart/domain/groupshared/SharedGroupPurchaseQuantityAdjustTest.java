package com.cartmate.cartmate.boundedContext.cart.domain.groupshared;

import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedGroupPurchaseQuantityAdjustTest {

    private static final Long CREATOR = 1L;
    private static final Long PARTICIPANT = 2L;
    private static final Long OUTSIDER = 3L;
    private static final Long ITEM_ID = 1L;

    private final LocalDateTime createdAt = LocalDateTime.of(2026, 10, 15, 12, 0);

    @Test
    @DisplayName("현재 수량에 증감값을 더하고 마지막 수정자를 바꾼다")
    void adjust() {
        SharedGroupPurchase purchase = purchaseWithQuantity(5);

        SharedGroupPurchaseItem item = purchase.adjustItemQuantity(PARTICIPANT, ITEM_ID, 3, createdAt);
        assertThat(item.getQuantity()).isEqualTo(8);
        assertThat(item.getUpdatedMemberId()).isEqualTo(PARTICIPANT);

        purchase.adjustItemQuantity(CREATOR, ITEM_ID, -7, createdAt);
        assertThat(item.getQuantity()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {-5, -6})
    @DisplayName("결과가 1 미만이면 거절하고 수량은 그대로다")
    void belowMinimum(int delta) {
        SharedGroupPurchase purchase = purchaseWithQuantity(5);

        assertThatThrownBy(() -> purchase.adjustItemQuantity(CREATOR, ITEM_ID, delta, createdAt))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(CartErrorCode.CART_INVALID_QUANTITY.getMsg());
        assertThat(purchase.getItems().getFirst().getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("결과가 999를 넘으면 거절한다")
    void aboveMaximum() {
        SharedGroupPurchase purchase = purchaseWithQuantity(990);

        assertThatThrownBy(() -> purchase.adjustItemQuantity(CREATOR, ITEM_ID, 10, createdAt))
                .hasMessageContaining(CartErrorCode.CART_INVALID_QUANTITY.getMsg());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 999, -999})
    @DisplayName("증감값이 0이거나 범위를 벗어나면 거절한다")
    void invalidDelta(int delta) {
        SharedGroupPurchase purchase = purchaseWithQuantity(5);

        assertThatThrownBy(() -> purchase.adjustItemQuantity(CREATOR, ITEM_ID, delta, createdAt))
                .hasMessageContaining(CartErrorCode.CART_INVALID_QUANTITY_DELTA.getMsg());
    }

    @Test
    @DisplayName("참여자가 아니면 수량을 바꿀 수 없다")
    void outsider() {
        SharedGroupPurchase purchase = purchaseWithQuantity(5);

        assertThatThrownBy(() -> purchase.adjustItemQuantity(OUTSIDER, ITEM_ID, 1, createdAt))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("수정 기한이 지나면 수량을 바꿀 수 없다")
    void afterDeadline() {
        SharedGroupPurchase purchase = purchaseWithQuantity(5);

        assertThatThrownBy(() -> purchase.adjustItemQuantity(CREATOR, ITEM_ID, 1,
                createdAt.plus(SharedGroupPurchase.EDIT_PERIOD)))
                .isInstanceOf(ServiceException.class);
    }

    private SharedGroupPurchase purchaseWithQuantity(int quantity) {
        SharedGroupPurchase purchase = SharedGroupPurchase.create("FC 훈련용품", CREATOR, 5, "abcdefghijkl", createdAt);
        purchase.join(PARTICIPANT, createdAt);
        purchase.addItem(CREATOR, 100L, 10L, quantity, createdAt);
        ReflectionTestUtils.setField(purchase.getItems().getFirst(), "id", ITEM_ID);
        return purchase;
    }
}
