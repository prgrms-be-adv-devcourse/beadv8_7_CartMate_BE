package com.cartmate.cartmate.boundedContext.cart.dto.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.out.CartProductOptions;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderLine;
import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchaseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;


class IndividualGroupPurchaseDashboardResponseTest {

    private static final Long UNIFORM = 100L;
    private static final Long SOCKS = 200L;
    private static final Long UNIFORM_M = 1L;
    private static final Long UNIFORM_L = 2L;
    private static final Long UNIFORM_XL = 3L;
    private static final Long SOCKS_FREE = 4L;
    private static final Long UNIFORM_DELETED = 9L;

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 15, 12, 0);

    @Test
    @DisplayName("상품별·옵션별·구매자별 수량과 금액을 집계하고, 모든 합계가 서로 맞는다")
    void aggregate() {
        IndividualGroupPurchase purchase = IndividualGroupPurchase.create(
                "OO FC 2026 유니폼", 1L, "abcdefghijkl", List.of(UNIFORM, SOCKS), now);

        Map<Long, CartProductOptions> productOptions = Map.of(
                UNIFORM, new CartProductOptions(UNIFORM, "축구 유니폼", null, true, List.of(
                        new CartProductOptions.Option(UNIFORM_M, "M", 39000, true),
                        new CartProductOptions.Option(UNIFORM_L, "L", 39000, true),
                        new CartProductOptions.Option(UNIFORM_XL, "XL", 41000, true))),
                SOCKS, new CartProductOptions(SOCKS, "양말", null, true, List.of(
                        new CartProductOptions.Option(SOCKS_FREE, "FREE", 5000, true))));


        List<IndividualGroupPurchaseOrderLine> lines = List.of(
                line(10L, 101L, UNIFORM, UNIFORM_M, 1, 39000),
                line(10L, 101L, SOCKS, SOCKS_FREE, 2, 5000),
                line(11L, 102L, UNIFORM, UNIFORM_L, 1, 39000),
                line(12L, 103L, UNIFORM, UNIFORM_DELETED, 1, 35000),
                line(13L, 102L, UNIFORM, UNIFORM_M, 1, 39000));

        IndividualGroupPurchaseDashboardResponse response =
                IndividualGroupPurchaseDashboardResponse.of(purchase, productOptions, lines,
                        Map.of(101L, "철수", 102L, "영희"), now);

        assertThat(response.orderCount()).isEqualTo(4);
        assertThat(response.buyerCount()).isEqualTo(3);
        assertThat(response.totalQuantity()).isEqualTo(6);
        assertThat(response.totalAmount()).isEqualTo(162_000L);

        var uniform = response.products().get(0);
        assertThat(uniform.productName()).isEqualTo("축구 유니폼");
        assertThat(uniform.quantity()).isEqualTo(4);
        assertThat(uniform.amount()).isEqualTo(152_000L);
        assertThat(uniform.options())
                .extracting(IndividualGroupPurchaseDashboardResponse.OptionSummary::inventoryId,
                        IndividualGroupPurchaseDashboardResponse.OptionSummary::quantity)
                .containsExactly(
                        tuple(UNIFORM_M, 2),
                        tuple(UNIFORM_L, 1),
                        tuple(UNIFORM_XL, 0),
                        tuple(UNIFORM_DELETED, 1));
        assertThat(uniform.options().stream().mapToLong(IndividualGroupPurchaseDashboardResponse.OptionSummary::amount).sum())
                .isEqualTo(uniform.amount());

        var socks = response.products().get(1);
        assertThat(socks.quantity()).isEqualTo(2);
        assertThat(socks.amount()).isEqualTo(10_000L);

        assertThat(response.buyers())
                .extracting(IndividualGroupPurchaseDashboardResponse.BuyerSummary::memberId)
                .containsExactly(101L, 102L, 103L);
        assertThat(response.buyers())
                .extracting(IndividualGroupPurchaseDashboardResponse.BuyerSummary::nickname)
                .containsExactly("철수", "영희", null);
        var buyer102 = response.buyers().get(1);
        assertThat(buyer102.quantity()).isEqualTo(2);
        assertThat(buyer102.amount()).isEqualTo(78_000L);
        assertThat(buyer102.lines()).extracting(IndividualGroupPurchaseDashboardResponse.BuyerLine::optionName)
                .containsExactly("L", "M");

        assertThat(response.buyers().stream().mapToLong(IndividualGroupPurchaseDashboardResponse.BuyerSummary::amount).sum())
                .isEqualTo(response.totalAmount());
    }

    @Test
    @DisplayName("주문이 없으면 모든 상품과 옵션이 0으로 보이고 구매자 목록은 비어 있다")
    void noOrders() {
        IndividualGroupPurchase purchase = IndividualGroupPurchase.create(
                "OO FC 2026 유니폼", 1L, "abcdefghijkl", List.of(SOCKS), now);
        Map<Long, CartProductOptions> productOptions = Map.of(
                SOCKS, new CartProductOptions(SOCKS, "양말", null, true, List.of(
                        new CartProductOptions.Option(SOCKS_FREE, "FREE", 5000, true))));

        IndividualGroupPurchaseDashboardResponse response =
                IndividualGroupPurchaseDashboardResponse.of(purchase, productOptions, List.of(), Map.of(), now);

        assertThat(response.orderCount()).isZero();
        assertThat(response.totalAmount()).isZero();
        assertThat(response.products().get(0).options().get(0).quantity()).isZero();
        assertThat(response.buyers()).isEmpty();
    }

    @Test
    @DisplayName("옵션명이 없는 단일 옵션 상품이어도 현황판을 만들고 옵션명은 null로 보인다")
    void nullOptionName() {
        IndividualGroupPurchase purchase = IndividualGroupPurchase.create(
                "OO FC 2026 유니폼", 1L, "abcdefghijkl", List.of(UNIFORM, SOCKS), now);
        Map<Long, CartProductOptions> productOptions = Map.of(
                UNIFORM, new CartProductOptions(UNIFORM, "축구 유니폼", null, true, List.of(
                        new CartProductOptions.Option(UNIFORM, null, 39000, true))),
                SOCKS, new CartProductOptions(SOCKS, "양말", null, true, List.of(
                        new CartProductOptions.Option(SOCKS, null, 5000, true))));
        List<IndividualGroupPurchaseOrderLine> lines = List.of(
                line(10L, 101L, UNIFORM, UNIFORM, 2, 39000),
                line(11L, 102L, SOCKS, SOCKS, 1, 5000));

        IndividualGroupPurchaseDashboardResponse response =
                IndividualGroupPurchaseDashboardResponse.of(purchase, productOptions, lines, Map.of(101L, "철수"), now);

        assertThat(response.totalAmount()).isEqualTo(83_000L);
        assertThat(response.products().get(0).options())
                .extracting(IndividualGroupPurchaseDashboardResponse.OptionSummary::optionName)
                .containsExactly((String) null);
        assertThat(response.buyers())
                .flatExtracting(IndividualGroupPurchaseDashboardResponse.BuyerSummary::lines)
                .extracting(IndividualGroupPurchaseDashboardResponse.BuyerLine::productName,
                        IndividualGroupPurchaseDashboardResponse.BuyerLine::optionName)
                .containsExactly(tuple("축구 유니폼", null), tuple("양말", null));
        assertThat(response.status()).isEqualTo(IndividualGroupPurchaseStatus.OPEN);
        assertThat(response.closedAt()).isNull();
    }

    @Test
    @DisplayName("마감된 공동구매는 상태와 마감 시각을 함께 보여 준다")
    void closedStatus() {
        IndividualGroupPurchase purchase = IndividualGroupPurchase.create(
                "OO FC 2026 유니폼", 1L, "abcdefghijkl", List.of(SOCKS), now);
        LocalDateTime closedAt = now.plusHours(1);
        purchase.close(1L, closedAt);
        Map<Long, CartProductOptions> productOptions = Map.of(
                SOCKS, new CartProductOptions(SOCKS, "양말", null, true, List.of(
                        new CartProductOptions.Option(SOCKS, null, 5000, true))));

        IndividualGroupPurchaseDashboardResponse response =
                IndividualGroupPurchaseDashboardResponse.of(purchase, productOptions, List.of(), Map.of(), closedAt);

        assertThat(response.status()).isEqualTo(IndividualGroupPurchaseStatus.CLOSED);
        assertThat(response.closedAt()).isEqualTo(closedAt);
        assertThat(response.open()).isFalse();
    }

    private IndividualGroupPurchaseOrderLine line(Long orderId, Long buyer, Long productId, Long inventoryId,
                                                  int quantity, long unitPrice) {
        return new IndividualGroupPurchaseOrderLine(orderId, buyer, productId, inventoryId, quantity, unitPrice, now);
    }
}
