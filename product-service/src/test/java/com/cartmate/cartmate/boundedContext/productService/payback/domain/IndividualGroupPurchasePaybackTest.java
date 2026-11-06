package com.cartmate.cartmate.boundedContext.productService.payback.domain;

import com.cartmate.cartmate.shared.product.PaybackFunder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IndividualGroupPurchasePaybackTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);

    @Test
    @DisplayName("처음 들어온 상품만 새로 들어온 상품으로 돌려주고, 빠졌다 다시 들어와도 새로 치지 않는다")
    void entersProductOnlyOnce() {
        IndividualGroupPurchasePayback payback = new IndividualGroupPurchasePayback();

        assertThat(payback.applyOpenState(NOW.plusDays(1), List.of(1L, 2L), 0)).containsExactly(1L, 2L);
        assertThat(payback.applyOpenState(NOW.plusDays(1), List.of(2L), 1)).isEmpty();
        assertThat(payback.applyOpenState(NOW.plusDays(1), List.of(1L, 3L), 2)).containsExactly(3L);
        assertThat(payback.getEnteredProductIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("스냅샷은 들어온 상품 것만, 같은 상품 · 부담 주체는 처음 값만 남긴다")
    void snapshotKeepsFirstValue() {
        IndividualGroupPurchasePayback payback = new IndividualGroupPurchasePayback();
        payback.applyOpenState(NOW.plusDays(1), List.of(1L), 0);

        payback.snapshot(List.of(
                PaybackConditionSnapshot.of(1L, PaybackFunder.ADMIN, null, 10, 500L, null),
                PaybackConditionSnapshot.of(2L, PaybackFunder.ADMIN, null, 10, 500L, null)));
        payback.snapshot(List.of(PaybackConditionSnapshot.of(1L, PaybackFunder.ADMIN, null, 20, 900L, null)));

        assertThat(payback.getConditionSnapshots()).singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.getProductId()).isEqualTo(1L);
                    assertThat(snapshot.getAmountPerUnit()).isEqualTo(500L);
                });
    }

    @Test
    @DisplayName("낮거나 같은 revision의 열림 상태는 마감 시각을 덮어쓰지 않는다")
    void ignoresOlderRevision() {
        IndividualGroupPurchasePayback payback = new IndividualGroupPurchasePayback();
        payback.applyOpenState(NOW.plusDays(2), List.of(1L), 3);

        payback.applyOpenState(NOW.plusDays(1), List.of(1L), 2);

        assertThat(payback.getDeadlineAt()).isEqualTo(NOW.plusDays(2));
        assertThat(payback.getRevision()).isEqualTo(3);
    }

    @Test
    @DisplayName("종료가 열림보다 먼저 오면 끝난 것으로 보지 않다가, 열림이 오면 닫힌 상태 그대로 끝난 것으로 본다")
    void closedBeforeOpened() {
        IndividualGroupPurchasePayback payback = new IndividualGroupPurchasePayback();

        payback.applyClosed(NOW.plusDays(1), NOW, 5);
        assertThat(payback.isEnded(NOW)).isFalse();

        payback.applyOpenState(NOW.plusDays(3), List.of(1L), 0);
        assertThat(payback.isClosed()).isTrue();
        assertThat(payback.getDeadlineAt()).isEqualTo(NOW.plusDays(1));
        assertThat(payback.isEnded(NOW)).isTrue();
    }

    @Test
    @DisplayName("종료 이벤트가 없어도 마감 시각이 지나면 끝난 것으로 본다")
    void endedByDeadline() {
        IndividualGroupPurchasePayback payback = new IndividualGroupPurchasePayback();
        payback.applyOpenState(NOW, List.of(1L), 0);

        assertThat(payback.isEnded(NOW.minusSeconds(1))).isFalse();
        assertThat(payback.isEnded(NOW)).isTrue();
    }
}
