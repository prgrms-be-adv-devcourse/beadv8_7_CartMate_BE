package com.cartmate.cartmate.boundedContext.cart.domain.groupindividual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class IndividualGroupPurchaseTest {

    private static final Long CREATOR = 1L;
    private static final Long OTHER = 2L;
    private static final Long UNIFORM = 100L;
    private static final Long SOCKS = 200L;
    private static final Long BALL = 300L;

    private final LocalDateTime createdAt = LocalDateTime.of(2026, 10, 15, 12, 0);

    @Test
    @DisplayName("만들면 판매 중이고 기본 판매 기간은 48시간이며, 같은 상품을 두 번 넘겨도 한 번만 등록된다")
    void create() {
        IndividualGroupPurchase purchase = purchase(List.of(UNIFORM, SOCKS, UNIFORM));

        assertThat(purchase.getStatus()).isEqualTo(IndividualGroupPurchaseStatus.OPEN);
        assertThat(purchase.getDeadlineAt()).isEqualTo(createdAt.plusHours(48));
        assertThat(purchase.getProducts()).extracting(IndividualGroupPurchaseProduct::getProductId)
                .containsExactly(UNIFORM, SOCKS);

        assertThatThrownBy(() -> purchase(List.of()))
                .hasMessage("판매할 상품을 하나 이상 선택해야 합니다.");
    }

    @Test
    @DisplayName("판매 마감 시각 전에는 판매 목록의 상품만 살 수 있고, 마감 시각이 지나면 상태가 OPEN이어도 살 수 없다")
    void orderableUntilDeadline() {
        IndividualGroupPurchase purchase = purchase(List.of(UNIFORM));
        LocalDateTime beforeDeadline = createdAt.plusHours(47);
        LocalDateTime atDeadline = createdAt.plusHours(48);

        assertThatCode(() -> purchase.validateOrderable(UNIFORM, beforeDeadline)).doesNotThrowAnyException();
        assertThatThrownBy(() -> purchase.validateOrderable(BALL, beforeDeadline))
                .hasMessage("이 공동구매에서 판매하지 않는 상품입니다.");

        assertThat(purchase.getStatus()).isEqualTo(IndividualGroupPurchaseStatus.OPEN);
        assertThat(purchase.isOpen(atDeadline)).isFalse();
        assertThatThrownBy(() -> purchase.validateOrderable(UNIFORM, atDeadline))
                .hasMessage("판매가 마감된 공동구매입니다.");
    }

    @Test
    @DisplayName("조기 마감하면 더 이상 살 수 없고, 다시 마감할 수 없다")
    void close() {
        IndividualGroupPurchase purchase = purchase(List.of(UNIFORM));
        LocalDateTime now = createdAt.plusHours(1);

        assertThatThrownBy(() -> purchase.close(OTHER, now)).hasMessage("공동구매 생성자만 할 수 있습니다.");
        purchase.close(CREATOR, now);

        assertThat(purchase.getStatus()).isEqualTo(IndividualGroupPurchaseStatus.CLOSED);
        assertThat(purchase.getClosedAt()).isEqualTo(now);
        assertThat(purchase.isOpen(now)).isFalse();
        assertThatThrownBy(() -> purchase.close(CREATOR, now)).hasMessage("이미 마감된 공동구매입니다.");
    }

    @Test
    @DisplayName("판매 상품은 생성자만 추가할 수 있고, 이미 있는 상품이나 마감 후 추가는 거절된다")
    void addProduct() {
        IndividualGroupPurchase purchase = purchase(List.of(UNIFORM));
        LocalDateTime now = createdAt.plusHours(1);

        assertThatThrownBy(() -> purchase.addProduct(OTHER, SOCKS, now)).hasMessage("공동구매 생성자만 할 수 있습니다.");
        assertThatThrownBy(() -> purchase.addProduct(CREATOR, UNIFORM, now)).hasMessage("이미 판매 중인 상품입니다.");

        purchase.addProduct(CREATOR, SOCKS, now);
        assertThat(purchase.hasProduct(SOCKS)).isTrue();

        assertThatThrownBy(() -> purchase.addProduct(CREATOR, BALL, createdAt.plusHours(48)))
                .hasMessage("판매가 마감된 공동구매입니다.");
    }

    @Test
    @DisplayName("이미 산 사람이 있는 상품과 마지막 하나 남은 상품은 뺄 수 없다")
    void removeProduct() {
        IndividualGroupPurchase purchase = purchase(List.of(UNIFORM, SOCKS));
        LocalDateTime now = createdAt.plusHours(1);

        assertThatThrownBy(() -> purchase.removeProduct(CREATOR, SOCKS, true, now))
                .hasMessage("이미 구매한 사람이 있는 상품은 뺄 수 없습니다.");

        purchase.removeProduct(CREATOR, SOCKS, false, now);
        assertThat(purchase.hasProduct(SOCKS)).isFalse();

        assertThatThrownBy(() -> purchase.removeProduct(CREATOR, UNIFORM, false, now))
                .hasMessage("판매 상품은 하나 이상 있어야 합니다. 판매를 멈추려면 마감해 주세요.");
    }

    @Test
    @DisplayName("판매 마감 시각은 지금 이후부터 지금으로부터 48시간 이내로만 바꿀 수 있다")
    void changeDeadline() {
        IndividualGroupPurchase purchase = purchase(List.of(UNIFORM));
        LocalDateTime now = createdAt.plusHours(10);

        assertThatThrownBy(() -> purchase.changeDeadline(CREATOR, now, now))
                .hasMessage("판매 마감 시각은 지금 이후여야 합니다.");
        assertThatThrownBy(() -> purchase.changeDeadline(CREATOR, now.plusHours(48).plusMinutes(1), now))
                .hasMessage("판매 마감 시각은 지금으로부터 48시간 이내여야 합니다.");

        purchase.changeDeadline(CREATOR, now.plusHours(48), now);
        assertThat(purchase.getDeadlineAt()).isEqualTo(now.plusHours(48));

        purchase.changeDeadline(CREATOR, now.plusHours(1), now);
        assertThat(purchase.getDeadlineAt()).isEqualTo(now.plusHours(1));
    }

    private IndividualGroupPurchase purchase(List<Long> productIds) {
        return IndividualGroupPurchase.create("OO FC 2026 유니폼", CREATOR, "abcdefghijkl", productIds, createdAt);
    }
}
