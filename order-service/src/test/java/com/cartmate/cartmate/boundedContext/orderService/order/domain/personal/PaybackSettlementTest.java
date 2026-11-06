package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.PaybackSettlement.ConfirmedLine;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.PaybackSettlement.PayoutLine;
import com.cartmate.cartmate.shared.cart.PaybackFunder;
import com.cartmate.cartmate.shared.cart.PaybackOverlapPolicy;
import com.cartmate.cartmate.shared.cart.dto.PaybackConditionPayload;
import com.cartmate.cartmate.shared.order.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PaybackSettlementTest {

    private static final Long BALL = 100L;
    private static final Long SHOE = 200L;
    private static final Long SELLER = 77L;

    @Test
    @DisplayName("결제 완료 주문이 모두 구매확정이면 취소 안 된 항목의 구매자 · 상품 · 수량을 모은다")
    void confirmedLines() {
        List<Order> orders = List.of(
                order(1L, 10L, OrderStatus.PAID, item(BALL, 3, false), item(SHOE, 1, true)),
                order(2L, 20L, OrderStatus.PAID, item(BALL, 2, false)));

        assertThat(PaybackSettlement.confirmedLines(orders, Set.of(1L, 2L))).hasValueSatisfying(lines ->
                assertThat(lines).containsExactly(new ConfirmedLine(10L, BALL, 3), new ConfirmedLine(20L, BALL, 2)));
    }

    @Test
    @DisplayName("구매확정 안 된 결제 완료 주문이나 결제 대기 주문이 남으면 판정하지 않는다")
    void notSettled() {
        assertThat(PaybackSettlement.confirmedLines(
                List.of(order(1L, 10L, OrderStatus.PAID), order(2L, 20L, OrderStatus.PAID)), Set.of(1L))).isEmpty();
        assertThat(PaybackSettlement.confirmedLines(
                List.of(order(3L, 30L, OrderStatus.PAYMENT_PENDING)), Set.of())).isEmpty();
    }

    @Test
    @DisplayName("취소된 주문은 끝난 것으로 보고 수량에서 뺀다")
    void canceledExcluded() {
        List<Order> orders = List.of(order(1L, 10L, OrderStatus.PAID, item(BALL, 1, false)),
                order(2L, 20L, OrderStatus.CANCELED, item(BALL, 5, false)));

        assertThat(PaybackSettlement.confirmedLines(orders, Set.of(1L)))
                .hasValueSatisfying(lines -> assertThat(lines).containsExactly(new ConfirmedLine(10L, BALL, 1)));
    }

    @Test
    @DisplayName("상품의 확정 수량이 기준 이상이면 구매자마다 개당 금액 × 자기 수량을 받고, 기준 미만이면 아무도 받지 않는다")
    void quantityThreshold() {
        List<ConfirmedLine> lines = List.of(new ConfirmedLine(10L, BALL, 3), new ConfirmedLine(20L, BALL, 1),
                new ConfirmedLine(10L, BALL, 1), new ConfirmedLine(30L, SHOE, 1));

        PaybackSettlement.Result result = PaybackSettlement.settle(lines,
                List.of(admin(BALL, 5, 500L), admin(SHOE, 2, 300L)), PaybackOverlapPolicy.BOTH);

        assertThat(result.paid()).isTrue();
        assertThat(result.payouts()).containsExactly(
                new PayoutLine(10L, BALL, PaybackFunder.ADMIN, null, 4, 500L),
                new PayoutLine(20L, BALL, PaybackFunder.ADMIN, null, 1, 500L));
        assertThat(result.payouts().getFirst().amount()).isEqualTo(2_000L);
        assertThat(result.conditionResults())
                .extracting(PaybackSettlement.ConditionResult::productId, PaybackSettlement.ConditionResult::confirmedQuantity,
                        PaybackSettlement.ConditionResult::reached, PaybackSettlement.ConditionResult::applied)
                .containsExactly(tuple(BALL, 5, true, true),
                        tuple(SHOE, 1, false, false));
    }

    @Test
    @DisplayName("조건이 없거나 주문이 없으면 지급이 없다")
    void nothingToPay() {
        assertThat(PaybackSettlement.settle(List.of(), List.of(admin(BALL, 2, 500L)), null).paid()).isFalse();
        assertThat(PaybackSettlement.settle(List.of(new ConfirmedLine(10L, BALL, 9)), List.of(), null).paid())
                .isFalse();
    }

    @Test
    @DisplayName("같은 구매자의 여러 주문 · 여러 항목 수량은 상품별로 합쳐 세고 지급도 한 줄로 받는다")
    void sameBuyerAggregated() {
        List<Order> orders = List.of(
                order(1L, 10L, OrderStatus.PAID, item(BALL, 2, false), item(SHOE, 1, false)),
                order(2L, 10L, OrderStatus.PAID, item(BALL, 3, false)),
                order(3L, 20L, OrderStatus.PAID, item(BALL, 1, false)));
        List<ConfirmedLine> lines = PaybackSettlement.confirmedLines(orders, Set.of(1L, 2L, 3L)).orElseThrow();

        PaybackSettlement.Result result = PaybackSettlement.settle(lines, List.of(admin(BALL, 6, 100L)), null);

        assertThat(result.payouts()).containsExactly(
                new PayoutLine(10L, BALL, PaybackFunder.ADMIN, null, 5, 100L),
                new PayoutLine(20L, BALL, PaybackFunder.ADMIN, null, 1, 100L));
    }

    @Test
    @DisplayName("조건이 하나뿐인 상품은 겹침 모드와 상관없이 그 조건대로 지급하고, 모드가 없으면 BOTH로 본다")
    void singleConditionIgnoresMode() {
        List<ConfirmedLine> lines = List.of(new ConfirmedLine(10L, BALL, 5));

        for (PaybackOverlapPolicy policy : PaybackOverlapPolicy.values()) {
            assertThat(PaybackSettlement.settle(lines, List.of(seller(BALL, 3, 300L)), policy).payouts())
                    .as("모드 %s", policy).extracting(PayoutLine::funder).containsExactly(PaybackFunder.SELLER);
        }
        assertThat(PaybackSettlement.settle(lines, List.of(admin(BALL, 5, 500L), seller(BALL, 3, 300L)), null)
                .payouts()).hasSize(2);
    }

    @Test
    @DisplayName("겹침 모드 BOTH: 같은 상품의 어드민 · 판매자 조건을 각각 달성하면 둘 다 지급한다")
    void overlapBoth() {
        PaybackSettlement.Result result = PaybackSettlement.settle(List.of(new ConfirmedLine(10L, BALL, 5)),
                List.of(admin(BALL, 5, 500L), seller(BALL, 3, 300L)), PaybackOverlapPolicy.BOTH);

        assertThat(result.payouts()).extracting(PayoutLine::funder)
                .containsExactly(PaybackFunder.ADMIN, PaybackFunder.SELLER);
    }

    @Test
    @DisplayName("겹침 모드 LARGER: 달성한 조건 중 개당 금액이 큰 쪽만, 같으면 어드민을 지급한다")
    void overlapLarger() {
        List<ConfirmedLine> lines = List.of(new ConfirmedLine(10L, BALL, 5));

        assertThat(PaybackSettlement.settle(lines, List.of(admin(BALL, 5, 500L), seller(BALL, 3, 800L)),
                PaybackOverlapPolicy.LARGER).payouts()).extracting(PayoutLine::funder)
                .containsExactly(PaybackFunder.SELLER);
        assertThat(PaybackSettlement.settle(lines, List.of(admin(BALL, 6, 500L), seller(BALL, 3, 300L)),
                PaybackOverlapPolicy.LARGER).payouts()).extracting(PayoutLine::funder)
                .containsExactly(PaybackFunder.SELLER);
        assertThat(PaybackSettlement.settle(lines, List.of(admin(BALL, 5, 500L), seller(BALL, 3, 500L)),
                PaybackOverlapPolicy.LARGER).payouts()).extracting(PayoutLine::funder)
                .containsExactly(PaybackFunder.ADMIN);
    }

    @Test
    @DisplayName("겹침 모드 ADMIN_ONLY · SELLER_ONLY: 겹친 상품은 고른 쪽만 보고 미달이면 지급하지 않으며, 겹치지 않은 상품은 그대로 지급한다")
    void overlapOnly() {
        List<ConfirmedLine> lines = List.of(new ConfirmedLine(10L, BALL, 5), new ConfirmedLine(10L, SHOE, 5));
        List<ReplicaPaybackCondition> conditions =
                List.of(admin(BALL, 6, 500L), seller(BALL, 3, 300L), seller(SHOE, 3, 100L));

        assertThat(PaybackSettlement.settle(lines, conditions, PaybackOverlapPolicy.ADMIN_ONLY).payouts())
                .extracting(PayoutLine::productId, PayoutLine::funder)
                .containsExactly(tuple(SHOE, PaybackFunder.SELLER));
        assertThat(PaybackSettlement.settle(lines, conditions, PaybackOverlapPolicy.SELLER_ONLY).payouts())
                .extracting(PayoutLine::productId, PayoutLine::funder, PayoutLine::sellerId)
                .containsExactly(tuple(BALL, PaybackFunder.SELLER, SELLER),
                        tuple(SHOE, PaybackFunder.SELLER, SELLER));
    }

    @Test
    @DisplayName("판정 결과는 조건별 기록을 남기고, 적용된 조건이 하나라도 있으면 지급으로 표시한다")
    void judgmentRecord() {
        PaybackSettlement.Result result = PaybackSettlement.settle(List.of(new ConfirmedLine(10L, BALL, 5)),
                List.of(admin(BALL, 6, 500L), seller(BALL, 3, 300L)), PaybackOverlapPolicy.BOTH);

        IndividualGroupPurchasePayback judged = IndividualGroupPurchasePayback.judge(1L, result,
                LocalDateTime.of(2026, 11, 20, 3, 0));

        assertThat(judged.isPaid()).isTrue();
        assertThat(judged.getConditionJudgments())
                .extracting(PaybackConditionJudgment::getFunder, PaybackConditionJudgment::isReached,
                        PaybackConditionJudgment::isApplied)
                .containsExactly(tuple(PaybackFunder.ADMIN, false, false),
                        tuple(PaybackFunder.SELLER, true, true));
    }

    private static ReplicaPaybackCondition admin(Long productId, int minQuantity, long amountPerUnit) {
        return ReplicaPaybackCondition.from(
                new PaybackConditionPayload(productId, PaybackFunder.ADMIN, null, minQuantity, amountPerUnit));
    }

    private static ReplicaPaybackCondition seller(Long productId, int minQuantity, long amountPerUnit) {
        return ReplicaPaybackCondition.from(
                new PaybackConditionPayload(productId, PaybackFunder.SELLER, SELLER, minQuantity, amountPerUnit));
    }

    private static Order order(Long id, Long buyer, OrderStatus status, OrderItem... items) {
        Order order = mock(Order.class);
        given(order.getId()).willReturn(id);
        given(order.getBuyerMemberId()).willReturn(buyer);
        given(order.getStatus()).willReturn(status);
        given(order.getItems()).willReturn(List.of(items));
        return order;
    }

    private static OrderItem item(Long productId, int quantity, boolean canceled) {
        OrderItem item = mock(OrderItem.class);
        given(item.getProductId()).willReturn(productId);
        given(item.getQuantity()).willReturn(quantity);
        given(item.isCanceled()).willReturn(canceled);
        return item;
    }
}
