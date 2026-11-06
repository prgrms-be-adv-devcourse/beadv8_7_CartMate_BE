package com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared;

import com.cartmate.cartmate.shared.cart.SharedGroupPurchaseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import static com.cartmate.cartmate.boundedContext.orderService.cart.support.GroupPurchaseFixtures.sharedPurchase;
import static org.assertj.core.api.Assertions.assertThat;

// 마감(CLOSED) 뒤 공동 주문 생성 결과로 바뀌는 단계: CLOSED → ORDER_CREATED, CLOSED → FROZEN 복귀
class SharedGroupPurchaseOrderStageTest {

    private static final Long CREATOR = 1L;
    private static final Long PARTICIPANT = 2L;
    private static final Long BALL_SKU = 10L;
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final LocalDateTime createdAt = LocalDateTime.of(2026, 11, 4, 12, 0);
    private final LocalDateTime closedAt = createdAt.plusHours(1);

    @Test
    @DisplayName("마감 대기에서 공동 주문 생성 소식을 받으면 ORDER_CREATED가 되고, 같은 소식이 다시 와도 그대로다")
    void closedToOrderCreated() {
        SharedGroupPurchase purchase = closedPurchase();

        assertThat(purchase.markOrderCreated()).isTrue();
        assertThat(purchase.getStatus()).isEqualTo(SharedGroupPurchaseStatus.ORDER_CREATED);

        assertThat(purchase.markOrderCreated()).isFalse();
        assertThat(purchase.getStatus()).isEqualTo(SharedGroupPurchaseStatus.ORDER_CREATED);
    }

    @Test
    @DisplayName("생성 실패면 마감 대기에서 마감 보류로 돌아가고, 생성자가 다시 마감할 수 있다")
    void closedRevertsToFrozenThenRecloses() {
        SharedGroupPurchase purchase = closedPurchase();
        LocalDateTime failedAt = closedAt.plusSeconds(2);

        assertThat(purchase.revertToFrozen(failedAt)).isTrue();
        assertThat(purchase.getStatus()).isEqualTo(SharedGroupPurchaseStatus.FROZEN);
        assertThat(purchase.getFrozenAt()).isEqualTo(failedAt);

        assertThat(purchase.revertToFrozen(failedAt.plusSeconds(1))).isFalse();
        assertThat(purchase.getFrozenAt()).isEqualTo(failedAt);

        LocalDateTime reclosedAt = failedAt.plusMinutes(5);
        purchase.close(CREATOR, Set.of(), reclosedAt);
        assertThat(purchase.getStatus()).isEqualTo(SharedGroupPurchaseStatus.CLOSED);
        assertThat(purchase.getClosedAt()).isEqualTo(reclosedAt);
    }

    @Test
    @DisplayName("주문이 이미 만들어진 뒤 늦게 온 생성 실패는 무시한다")
    void failureAfterOrderCreatedIsIgnored() {
        SharedGroupPurchase purchase = closedPurchase();
        purchase.markOrderCreated();

        assertThat(purchase.revertToFrozen(closedAt.plusMinutes(1))).isFalse();
        assertThat(purchase.getStatus()).isEqualTo(SharedGroupPurchaseStatus.ORDER_CREATED);
        assertThat(purchase.getFrozenAt()).isNull();
    }

    @Test
    @DisplayName("시간 초과로 마감 보류에 돌아간 뒤 늦게 온 생성 소식도 받는다(주문이 이미 있으므로)")
    void lateOrderCreatedAfterTimeout() {
        SharedGroupPurchase purchase = closedPurchase();
        purchase.revertToFrozen(closedAt.plus(TIMEOUT));

        assertThat(purchase.markOrderCreated()).isTrue();
        assertThat(purchase.getStatus()).isEqualTo(SharedGroupPurchaseStatus.ORDER_CREATED);
    }

    @Test
    @DisplayName("모집 중 · 취소된 공동구매는 생성 소식 · 생성 실패를 받아도 바뀌지 않는다")
    void otherStatusesAreRejected() {
        SharedGroupPurchase open = openPurchase();
        assertThat(open.markOrderCreated()).isFalse();
        assertThat(open.revertToFrozen(closedAt)).isFalse();
        assertThat(open.getStatus()).isEqualTo(SharedGroupPurchaseStatus.OPEN);

        SharedGroupPurchase canceled = openPurchase();
        canceled.cancel(CREATOR, closedAt);
        assertThat(canceled.markOrderCreated()).isFalse();
        assertThat(canceled.revertToFrozen(closedAt)).isFalse();
        assertThat(canceled.getStatus()).isEqualTo(SharedGroupPurchaseStatus.CANCELED);
    }

    @Test
    @DisplayName("마감 대기가 제한 시간을 넘겨야 시간 초과다. 주문이 만들어졌으면 시간 초과가 아니다")
    void overdue() {
        SharedGroupPurchase purchase = closedPurchase();

        assertThat(purchase.isOrderCreationOverdue(TIMEOUT, closedAt.plus(TIMEOUT).minusSeconds(1))).isFalse();
        assertThat(purchase.isOrderCreationOverdue(TIMEOUT, closedAt.plus(TIMEOUT))).isTrue();

        purchase.markOrderCreated();
        assertThat(purchase.isOrderCreationOverdue(TIMEOUT, closedAt.plus(TIMEOUT))).isFalse();
    }

    @Test
    @DisplayName("주문까지 만들어진 공동구매도 다시 만들기(복제)할 수 있다")
    void duplicateAfterOrderCreated() {
        SharedGroupPurchase purchase = closedPurchase();
        purchase.markOrderCreated();

        SharedGroupPurchase copy = purchase.duplicate(CREATOR, "copycopycopy", Set.of(), closedAt.plusDays(1));

        assertThat(copy.getStatus()).isEqualTo(SharedGroupPurchaseStatus.OPEN);
        assertThat(copy.getItems()).extracting(SharedGroupPurchaseItem::getInventoryId).containsExactly(BALL_SKU);
    }

    private SharedGroupPurchase closedPurchase() {
        SharedGroupPurchase purchase = openPurchase();
        purchase.close(CREATOR, Set.of(), closedAt);
        return purchase;
    }

    private SharedGroupPurchase openPurchase() {
        SharedGroupPurchase purchase = sharedPurchase(CREATOR, PARTICIPANT, createdAt);
        purchase.addItem(CREATOR, 100L, BALL_SKU, 2, createdAt);
        ReflectionTestUtils.setField(purchase, "id", 1L);
        return purchase;
    }
}
