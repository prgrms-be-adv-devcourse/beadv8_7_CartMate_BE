package com.cartmate.cartmate.boundedContext.cart.domain.groupshared;

import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedGroupPurchaseFrozenTest {

    private static final Long CREATOR = 1L;
    private static final Long PARTICIPANT = 2L;
    private static final Long BALL_PRODUCT = 100L;
    private static final Long BALL_SKU = 10L;
    private static final Long PIN_PRODUCT = 200L;
    private static final Long PIN_SKU = 20L;

    private final LocalDateTime createdAt = LocalDateTime.of(2026, 10, 15, 12, 0);
    private final LocalDateTime deadline = createdAt.plus(SharedGroupPurchase.EDIT_PERIOD);

    @Test
    @DisplayName("기한 도달 시 판매 불가 상품이 있으면 마감 보류(FROZEN)가 되고, 생성자가 정리하면 마감된다")
    void frozenThenCreatorResolves() {
        SharedGroupPurchase purchase = purchaseWithBallAndPin();

        purchase.closeByDeadline(Set.of(PIN_SKU), deadline);
        assertThat(purchase.getStatus()).isEqualTo(SharedGroupPurchaseStatus.FROZEN);
        assertThat(purchase.getFrozenAt()).isEqualTo(deadline);

        Long pinItemId = itemIdOf(purchase, PIN_SKU);
        LocalDateTime afterFrozen = deadline.plusHours(1);

        assertThatThrownBy(() -> purchase.removeItem(PARTICIPANT, pinItemId, afterFrozen))
                .hasMessageContaining("수정이 마감된 공동구매입니다.");

        assertThatThrownBy(() -> purchase.close(CREATOR, Set.of(PIN_SKU), afterFrozen))
                .hasMessageContaining("구매할 수 없는 상품이 있어 마감할 수 없습니다.");

        purchase.removeItem(CREATOR, pinItemId, afterFrozen);
        purchase.close(CREATOR, Set.of(PIN_SKU), afterFrozen);

        assertThat(purchase.getStatus()).isEqualTo(SharedGroupPurchaseStatus.CLOSED);
        assertThat(purchase.getItems()).extracting(SharedGroupPurchaseItem::getInventoryId).containsExactly(BALL_SKU);
    }

    @Test
    @DisplayName("마감 보류 후 24시간이 지나면 판매 불가 상품을 빼고 자동 마감된다")
    void frozenTimeoutClosesWithoutUnavailable() {
        SharedGroupPurchase purchase = purchaseWithBallAndPin();
        purchase.closeByDeadline(Set.of(PIN_SKU), deadline);

        LocalDateTime beforeTimeout = deadline.plus(SharedGroupPurchase.FROZEN_PERIOD).minusMinutes(1);
        assertThatThrownBy(() -> purchase.closeByFrozenTimeout(Set.of(PIN_SKU), beforeTimeout))
                .isInstanceOf(ServiceException.class);

        purchase.closeByFrozenTimeout(Set.of(PIN_SKU), deadline.plus(SharedGroupPurchase.FROZEN_PERIOD));

        assertThat(purchase.getStatus()).isEqualTo(SharedGroupPurchaseStatus.CLOSED);
        assertThat(purchase.getItems()).extracting(SharedGroupPurchaseItem::getInventoryId).containsExactly(BALL_SKU);
    }

    @Test
    @DisplayName("마감 보류 후 24시간이 지났는데 남는 상품이 없으면 취소(CANCELED)된다")
    void frozenTimeoutCancelsWhenNothingLeft() {
        SharedGroupPurchase purchase = purchaseWithBallAndPin();
        Set<Long> allUnavailable = Set.of(BALL_SKU, PIN_SKU);
        purchase.closeByDeadline(allUnavailable, deadline);

        purchase.closeByFrozenTimeout(allUnavailable, deadline.plus(SharedGroupPurchase.FROZEN_PERIOD));

        assertThat(purchase.getStatus()).isEqualTo(SharedGroupPurchaseStatus.CANCELED);
        assertThat(purchase.getItems()).isEmpty();
    }

    @Test
    @DisplayName("기한 도달 시 판매 불가 상품이 없으면 바로 마감되고, 항목이 없으면 취소된다")
    void deadlineWithoutProblem() {
        SharedGroupPurchase withItems = purchaseWithBallAndPin();
        withItems.closeByDeadline(Set.of(), deadline);
        assertThat(withItems.getStatus()).isEqualTo(SharedGroupPurchaseStatus.CLOSED);

        SharedGroupPurchase empty = SharedGroupPurchase.create("빈 공동구매", CREATOR, 5, "emptyemptyem", createdAt);
        empty.closeByDeadline(Set.of(), deadline);
        assertThat(empty.getStatus()).isEqualTo(SharedGroupPurchaseStatus.CANCELED);
    }

    private SharedGroupPurchase purchaseWithBallAndPin() {
        SharedGroupPurchase purchase = SharedGroupPurchase.create("FC 훈련용품", CREATOR, 5, "abcdefghijkl", createdAt);
        purchase.join(PARTICIPANT, createdAt);
        purchase.addItem(CREATOR, BALL_PRODUCT, BALL_SKU, 2, createdAt);
        purchase.addItem(PARTICIPANT, PIN_PRODUCT, PIN_SKU, 10, createdAt);
        long id = 1;
        for (SharedGroupPurchaseItem item : purchase.getItems()) {
            ReflectionTestUtils.setField(item, "id", id++);
        }
        return purchase;
    }

    private Long itemIdOf(SharedGroupPurchase purchase, Long inventoryId) {
        return purchase.getItems().stream()
                .filter(item -> item.getInventoryId().equals(inventoryId))
                .findFirst()
                .orElseThrow()
                .getId();
    }
}
