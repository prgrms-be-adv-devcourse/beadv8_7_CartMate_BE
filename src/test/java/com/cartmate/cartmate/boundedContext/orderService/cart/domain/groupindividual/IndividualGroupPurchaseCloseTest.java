package com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.exception.CartErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndividualGroupPurchaseCloseTest {

    private static final Long ADMIN = 1L;
    private static final Long PRODUCT = 100L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);
    private static final LocalDateTime DEADLINE = NOW.plusDays(3);

    @Test
    @DisplayName("어드민 공동구매는 만든 어드민이 아니어도 어드민 마감으로 닫히고, 수요 상품 키가 비워진다")
    void closeByAdmin() {
        IndividualGroupPurchase purchase = demand();
        assertThat(purchase.getDemandProductKey()).isEqualTo(PRODUCT);

        purchase.closeByAdmin(NOW.plusHours(1));

        assertThat(purchase.isClosed()).isTrue();
        assertThat(purchase.getClosedAt()).isEqualTo(NOW.plusHours(1));
        assertThat(purchase.getDemandProductKey()).isNull();
        assertThatThrownBy(() -> purchase.closeByAdmin(NOW.plusHours(2)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(CartErrorCode.CART_INDIVIDUAL_ALREADY_CLOSED.getMsg());
    }

    @Test
    @DisplayName("회원 공동구매는 어드민 마감 대상이 아니다 (회원 공동구매는 개설자만 마감)")
    void closeByAdminRejectsMemberPurchase() {
        IndividualGroupPurchase purchase = member();

        assertThatThrownBy(() -> purchase.closeByAdmin(NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(CartErrorCode.CART_INDIVIDUAL_ADMIN_DEMAND_ONLY.getMsg());
        assertThat(purchase.isClosed()).isFalse();
    }

    @Test
    @DisplayName("시스템 마감은 마감 시각이 지난 OPEN 어드민 공동구매만 닫는다. 마감 전 · 이미 닫힘 · 회원 공동구매는 그대로 두고 false")
    void closeByDeadline() {
        IndividualGroupPurchase purchase = demand();

        assertThat(purchase.closeByDeadline(DEADLINE.minusSeconds(1))).isFalse();
        assertThat(purchase.isClosed()).isFalse();

        assertThat(purchase.closeByDeadline(DEADLINE)).isTrue();
        assertThat(purchase.isClosed()).isTrue();
        assertThat(purchase.getDemandProductKey()).isNull();

        assertThat(purchase.closeByDeadline(DEADLINE.plusHours(1))).isFalse();
        assertThat(purchase.getClosedAt()).isEqualTo(DEADLINE);

        IndividualGroupPurchase memberPurchase = member();
        assertThat(memberPurchase.closeByDeadline(NOW.plusDays(10))).isFalse();
        assertThat(memberPurchase.isClosed()).isFalse();
    }

    @Test
    @DisplayName("판매 중지로 상품이 모두 빠져 닫혀도 수요 상품 키를 비운다 (같은 상품으로 다시 열 수 있게)")
    void salesStopCloseReleasesDemandKey() {
        IndividualGroupPurchase purchase = demand();

        boolean changed = purchase.removeProductsBySalesStopped(Set.of(PRODUCT), NOW.plusHours(1));

        assertThat(changed).isTrue();
        assertThat(purchase.isClosed()).isTrue();
        assertThat(purchase.getDemandProductKey()).isNull();
    }

    @Test
    @DisplayName("개설자 마감도 같은 종료 경로를 지나 수요 상품 키를 비운다")
    void creatorCloseReleasesDemandKey() {
        IndividualGroupPurchase purchase = demand();

        purchase.close(ADMIN, NOW.plusHours(1));

        assertThat(purchase.isClosed()).isTrue();
        assertThat(purchase.getDemandProductKey()).isNull();
    }

    private static IndividualGroupPurchase demand() {
        return IndividualGroupPurchase.openFromDemand("수요 공동구매", ADMIN, "INVITE000001", PRODUCT, DEADLINE, 10, 1000L, NOW);
    }

    private static IndividualGroupPurchase member() {
        return IndividualGroupPurchase.create("회원 공동구매", 2L, "INVITE000002", List.of(PRODUCT), NOW);
    }
}
