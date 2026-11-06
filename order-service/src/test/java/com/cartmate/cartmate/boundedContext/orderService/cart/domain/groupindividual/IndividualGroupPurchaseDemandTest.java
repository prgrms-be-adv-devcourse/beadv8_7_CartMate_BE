package com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual;

import com.cartmate.cartmate.shared.cart.dto.PaybackConditionPayload;
import com.cartmate.cartmate.shared.cart.PaybackOverlapPolicy;
import com.cartmate.cartmate.shared.cart.PaybackFunder;
import com.cartmate.cartmate.shared.cart.IndividualGroupPurchaseOrigin;
import com.cartmate.cartmate.boundedContext.orderService.cart.exception.CartErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.cartmate.cartmate.boundedContext.orderService.cart.support.GroupPurchaseFixtures.demandPurchase;
import static com.cartmate.cartmate.boundedContext.orderService.cart.support.GroupPurchaseFixtures.memberPurchase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndividualGroupPurchaseDemandTest {

    private static final Long ADMIN = 1L;
    private static final Long BALL = 300L;

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 29, 12, 0);

    @Test
    @DisplayName("수요로 열면 어드민 출처 · 원래 상품 · 중복 방지 키 · 페이백 조건이 저장되고 기본 판매 기간은 72시간이다")
    void openFromDemand() {
        IndividualGroupPurchase purchase = demandPurchase(ADMIN, BALL, null, now);

        assertThat(purchase.getOrigin()).isEqualTo(IndividualGroupPurchaseOrigin.ADMIN_DEMAND);
        assertThat(purchase.isFromDemand()).isTrue();
        assertThat(purchase.getSourceProductId()).isEqualTo(BALL);
        assertThat(purchase.getDemandProductKey()).isEqualTo(BALL);
        assertThat(purchase.getPaybackConditions()).singleElement().satisfies(condition -> {
            assertThat(condition.getProductId()).isEqualTo(BALL);
            assertThat(condition.getFunder()).isEqualTo(PaybackFunder.ADMIN);
            assertThat(condition.getSellerId()).isNull();
            assertThat(condition.getMinQuantity()).isEqualTo(10);
            assertThat(condition.getAmountPerUnit()).isEqualTo(1_000L);
        });
        assertThat(purchase.getPaybackOverlapPolicy()).isEqualTo(PaybackOverlapPolicy.BOTH);
        assertThat(purchase.getCreatorMemberId()).isEqualTo(ADMIN);
        assertThat(purchase.getProducts()).extracting(IndividualGroupPurchaseProduct::getProductId).containsExactly(BALL);
        assertThat(purchase.getDeadlineAt()).isEqualTo(now.plusHours(72));
        assertThat(purchase.isOpen(now)).isTrue();
    }

    @Test
    @DisplayName("회원이 만든 공동구매는 회원 출처이고 중복 방지 키와 페이백 조건이 없다")
    void memberOrigin() {
        IndividualGroupPurchase purchase = memberPurchase(2L, List.of(BALL), now);

        assertThat(purchase.getOrigin()).isEqualTo(IndividualGroupPurchaseOrigin.MEMBER);
        assertThat(purchase.getDemandProductKey()).isNull();
        assertThat(purchase.getPaybackConditions()).isEmpty();
        assertThat(purchase.getPaybackOverlapPolicy()).isEqualTo(PaybackOverlapPolicy.BOTH);
    }

    @Test
    @DisplayName("수요 공동구매의 판매 마감은 최대 7일이고, 회원 공동구매는 그대로 48시간이다")
    void deadlineLimit() {
        assertThatCode(() -> demandPurchase(ADMIN, BALL, now.plusDays(7), now)).doesNotThrowAnyException();
        assertThatThrownBy(() -> demandPurchase(ADMIN, BALL, now.plusDays(7).plusMinutes(1), now))
                .hasMessageContaining(CartErrorCode.CART_DEMAND_DEADLINE_TOO_LATE.getMsg());
        assertThatThrownBy(() -> demandPurchase(ADMIN, BALL, now, now))
                .hasMessageContaining(CartErrorCode.CART_INDIVIDUAL_DEADLINE_IN_PAST.getMsg());

        IndividualGroupPurchase demand = demandPurchase(ADMIN, BALL, null, now);
        assertThatCode(() -> demand.changeDeadline(ADMIN, now.plusDays(6), now)).doesNotThrowAnyException();

        IndividualGroupPurchase member = memberPurchase(2L, List.of(BALL), now);
        assertThatThrownBy(() -> member.changeDeadline(2L, now.plusDays(3), now))
                .hasMessageContaining(CartErrorCode.CART_INDIVIDUAL_DEADLINE_TOO_LATE.getMsg());
    }

    @Test
    @DisplayName("페이백 기준 수량은 2~100,000개, 개당 금액은 1~100,000원이어야 한다")
    void paybackRange() {
        assertThatThrownBy(() -> IndividualGroupPurchase.openFromDemand(
                "축구공", ADMIN, "invitecode01", BALL, null, 1, 1_000L, null, now))
                .hasMessageContaining(CartErrorCode.CART_DEMAND_INVALID_PAYBACK.getMsg());
        assertThatThrownBy(() -> IndividualGroupPurchase.openFromDemand(
                "축구공", ADMIN, "invitecode01", BALL, null, 100_001, 1_000L, null, now))
                .hasMessageContaining(CartErrorCode.CART_DEMAND_INVALID_PAYBACK.getMsg());
        assertThatThrownBy(() -> IndividualGroupPurchase.openFromDemand(
                "축구공", ADMIN, "invitecode01", BALL, null, 10, 0L, null, now))
                .hasMessageContaining(CartErrorCode.CART_DEMAND_INVALID_PAYBACK.getMsg());
        assertThatThrownBy(() -> IndividualGroupPurchase.openFromDemand(
                "축구공", ADMIN, "invitecode01", BALL, null, 10, 100_001L, null, now))
                .hasMessageContaining(CartErrorCode.CART_DEMAND_INVALID_PAYBACK.getMsg());
        assertThatCode(() -> IndividualGroupPurchase.openFromDemand(
                "축구공", ADMIN, "invitecode01", BALL, null, 100_000, 100_000L, null, now))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("수요 개설 때 고른 겹침 모드가 저장된다")
    void overlapPolicy() {
        IndividualGroupPurchase purchase = IndividualGroupPurchase.openFromDemand(
                "축구공", ADMIN, "invitecode01", BALL, null, 10, 500L, PaybackOverlapPolicy.LARGER, now);

        assertThat(purchase.getPaybackOverlapPolicy()).isEqualTo(PaybackOverlapPolicy.LARGER);
    }

    @Test
    @DisplayName("판매자 조건은 판매 중인 상품에만, 상품마다 한 번만 붙고 상품을 빼도 지워지지 않는다")
    void sellerCondition() {
        IndividualGroupPurchase purchase = memberPurchase(2L, List.of(BALL, 301L), now);

        assertThat(purchase.addSellerPaybackCondition(BALL, 77L, 100, 300L)).isTrue();
        assertThat(purchase.addSellerPaybackCondition(BALL, 77L, 50, 900L)).isFalse();
        assertThatThrownBy(() -> purchase.addSellerPaybackCondition(999L, 77L, 100, 300L))
                .hasMessageContaining(CartErrorCode.CART_INDIVIDUAL_PRODUCT_NOT_ON_SALE.getMsg());
        assertThat(purchase.findPaybackCondition(BALL, PaybackFunder.SELLER))
                .get().extracting(IndividualGroupPurchasePaybackCondition::getAmountPerUnit).isEqualTo(300L);
        assertThat(purchase.paybackConditionPayloads())
                .containsExactly(new PaybackConditionPayload(BALL, PaybackFunder.SELLER, 77L, 100, 300L));

        purchase.removeProduct(2L, BALL, false, now);

        assertThat(purchase.getPaybackConditions()).hasSize(1);
        purchase.addProduct(2L, BALL, now);
        assertThat(purchase.addSellerPaybackCondition(BALL, 77L, 10, 900L)).isFalse();
    }

    @Test
    @DisplayName("판매 중지로 마지막 상품이 빠져 닫혀도 종료 이벤트에 실을 조건이 남아 있다")
    void conditionsSurviveSalesStoppedClose() {
        IndividualGroupPurchase purchase = demandPurchase(ADMIN, BALL, null, now);

        purchase.removeProductsBySalesStopped(Set.of(BALL), now);

        assertThat(purchase.isClosed()).isTrue();
        assertThat(purchase.paybackConditionPayloads())
                .containsExactly(new PaybackConditionPayload(BALL, PaybackFunder.ADMIN, null, 10, 1_000L));
    }

    @Test
    @DisplayName("판매 중이면 키를 풀 수 없고, 마감 시각이 지났으면 상태가 OPEN이어도 키를 푼다")
    void releaseKey() {
        IndividualGroupPurchase purchase = demandPurchase(ADMIN, BALL, null, now);

        assertThatThrownBy(() -> purchase.releaseDemandProductKey(now.plusHours(1)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(CartErrorCode.CART_DEMAND_ALREADY_OPEN.getMsg());

        purchase.releaseDemandProductKey(now.plusHours(72));

        assertThat(purchase.getDemandProductKey()).isNull();
        assertThat(purchase.getStatus()).isEqualTo(IndividualGroupPurchaseStatus.OPEN);
    }
}
