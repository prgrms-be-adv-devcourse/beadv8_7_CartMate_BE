package com.cartmate.cartmate.boundedContext.productService.payback.domain;

import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackSettlement.ConfirmedLine;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackSettlement.PayoutLine;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.shared.product.PaybackFunder;
import com.cartmate.cartmate.shared.product.PaybackOverlapPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PaybackSettlementTest {

    private static final Long BALL = 100L;
    private static final Long SHOE = 200L;
    private static final Long SELLER = 77L;
    private static final Long OTHER_SELLER = 78L;

    @Test
    @DisplayName("결제 완료 주문의 판매자 배송이 모두 구매확정이면 판매자 취소 안 된 항목의 구매자 · 상품 · 수량을 모은다")
    void confirmedLines() {
        PaybackOrder first = paid(1L, 10L, item(1L, BALL, SELLER, 3), item(2L, SHOE, OTHER_SELLER, 1));
        first.applySellerItemsCanceled(OTHER_SELLER);
        first.applyConfirmed(SELLER);
        PaybackOrder second = paid(2L, 20L, item(3L, BALL, SELLER, 2));
        second.applyConfirmed(SELLER);

        assertThat(PaybackSettlement.confirmedLines(List.of(first, second))).hasValueSatisfying(lines ->
                assertThat(lines).containsExactly(new ConfirmedLine(10L, BALL, 3), new ConfirmedLine(20L, BALL, 2)));
    }

    @Test
    @DisplayName("한 판매자 배송이라도 구매확정 전이거나 결제 대기 · 상태 모름 주문이 남으면 판정하지 않는다")
    void notSettled() {
        PaybackOrder partlyConfirmed = paid(1L, 10L, item(1L, BALL, SELLER, 1), item(2L, SHOE, OTHER_SELLER, 1));
        partlyConfirmed.applyConfirmed(SELLER);
        assertThat(PaybackSettlement.confirmedLines(List.of(partlyConfirmed))).isEmpty();

        PaybackOrder pending = new PaybackOrder(2L);
        pending.applyPlaced(5L, 20L, List.of(item(3L, BALL, SELLER, 1)), OrderStatus.PAYMENT_PENDING);
        assertThat(PaybackSettlement.confirmedLines(List.of(pending))).isEmpty();
        assertThat(PaybackSettlement.confirmedLines(List.of(new PaybackOrder(3L)))).isEmpty();
    }

    @Test
    @DisplayName("취소된 주문과 모든 항목이 판매자 취소된 주문은 끝난 것으로 보고 수량에서 뺀다")
    void canceledExcluded() {
        PaybackOrder confirmed = paid(1L, 10L, item(1L, BALL, SELLER, 1));
        confirmed.applyConfirmed(SELLER);
        PaybackOrder canceled = paid(2L, 20L, item(2L, BALL, SELLER, 5));
        canceled.applyCanceled();
        PaybackOrder sellerCanceled = paid(3L, 30L, item(3L, BALL, SELLER, 4));
        sellerCanceled.applySellerItemsCanceled(SELLER);

        assertThat(PaybackSettlement.confirmedLines(List.of(confirmed, canceled, sellerCanceled)))
                .hasValueSatisfying(lines -> assertThat(lines).containsExactly(new ConfirmedLine(10L, BALL, 1)));
    }

    @Test
    @DisplayName("상품의 확정 수량이 기준 이상이면 구매자마다 개당 금액 × 자기 수량을 받고, 기준 미만이면 아무도 받지 않는다")
    void quantityThreshold() {
        List<ConfirmedLine> lines = List.of(new ConfirmedLine(10L, BALL, 3), new ConfirmedLine(20L, BALL, 1),
                new ConfirmedLine(10L, BALL, 1), new ConfirmedLine(30L, SHOE, 1));

        PaybackSettlement.Result result = PaybackSettlement.settle(lines,
                List.of(admin(BALL, 5, 500L, null), admin(SHOE, 2, 300L, null)));

        assertThat(result.paid()).isTrue();
        assertThat(result.payouts()).containsExactly(
                new PayoutLine(10L, BALL, PaybackFunder.ADMIN, null, 4, 500L),
                new PayoutLine(20L, BALL, PaybackFunder.ADMIN, null, 1, 500L));
        assertThat(result.payouts().getFirst().amount()).isEqualTo(2_000L);
        assertThat(result.conditionResults())
                .extracting(PaybackSettlement.ConditionResult::productId,
                        PaybackSettlement.ConditionResult::confirmedQuantity,
                        PaybackSettlement.ConditionResult::reached, PaybackSettlement.ConditionResult::applied)
                .containsExactly(tuple(BALL, 5, true, true), tuple(SHOE, 1, false, false));
    }

    @Test
    @DisplayName("조건이 없거나 주문이 없으면 지급이 없다")
    void nothingToPay() {
        assertThat(PaybackSettlement.settle(List.of(), List.of(admin(BALL, 2, 500L, null))).paid()).isFalse();
        assertThat(PaybackSettlement.settle(List.of(new ConfirmedLine(10L, BALL, 9)), List.of()).paid()).isFalse();
    }

    @Test
    @DisplayName("같은 구매자의 여러 주문 · 여러 항목 수량은 상품별로 합쳐 세고 지급도 한 줄로 받는다")
    void sameBuyerAggregated() {
        PaybackOrder first = paid(1L, 10L, item(1L, BALL, SELLER, 2), item(2L, SHOE, SELLER, 1));
        PaybackOrder second = paid(2L, 10L, item(3L, BALL, SELLER, 3));
        PaybackOrder third = paid(3L, 20L, item(4L, BALL, SELLER, 1));
        List.of(first, second, third).forEach(order -> order.applyConfirmed(SELLER));
        List<ConfirmedLine> lines = PaybackSettlement.confirmedLines(List.of(first, second, third)).orElseThrow();

        PaybackSettlement.Result result = PaybackSettlement.settle(lines, List.of(admin(BALL, 6, 100L, null)));

        assertThat(result.payouts()).containsExactly(
                new PayoutLine(10L, BALL, PaybackFunder.ADMIN, null, 5, 100L),
                new PayoutLine(20L, BALL, PaybackFunder.ADMIN, null, 1, 100L));
    }

    @Test
    @DisplayName("조건이 하나뿐인 상품은 겹침 모드와 상관없이 그 조건대로 지급하고, 어드민 모드가 없으면 BOTH로 본다")
    void singleConditionIgnoresMode() {
        List<ConfirmedLine> lines = List.of(new ConfirmedLine(10L, BALL, 5));

        for (PaybackOverlapPolicy policy : PaybackOverlapPolicy.values()) {
            assertThat(PaybackSettlement.settle(lines, List.of(admin(BALL, 3, 300L, policy))).payouts())
                    .as("모드 %s", policy).extracting(PayoutLine::funder).containsExactly(PaybackFunder.ADMIN);
        }
        assertThat(PaybackSettlement.settle(lines, List.of(admin(BALL, 5, 500L, null), seller(BALL, 3, 300L)))
                .payouts()).hasSize(2);
    }

    @Test
    @DisplayName("겹침 모드 BOTH: 같은 상품의 어드민 · 판매자 조건을 각각 달성하면 둘 다 지급한다")
    void overlapBoth() {
        PaybackSettlement.Result result = PaybackSettlement.settle(List.of(new ConfirmedLine(10L, BALL, 5)),
                List.of(admin(BALL, 5, 500L, PaybackOverlapPolicy.BOTH), seller(BALL, 3, 300L)));

        assertThat(result.payouts()).extracting(PayoutLine::funder)
                .containsExactly(PaybackFunder.ADMIN, PaybackFunder.SELLER);
    }

    @Test
    @DisplayName("겹침 모드 LARGER: 달성한 조건 중 개당 금액이 큰 쪽만, 같으면 어드민을 지급한다")
    void overlapLarger() {
        List<ConfirmedLine> lines = List.of(new ConfirmedLine(10L, BALL, 5));
        PaybackOverlapPolicy larger = PaybackOverlapPolicy.LARGER;

        assertThat(PaybackSettlement.settle(lines, List.of(admin(BALL, 5, 500L, larger), seller(BALL, 3, 800L)))
                .payouts()).extracting(PayoutLine::funder).containsExactly(PaybackFunder.SELLER);
        assertThat(PaybackSettlement.settle(lines, List.of(admin(BALL, 6, 500L, larger), seller(BALL, 3, 300L)))
                .payouts()).extracting(PayoutLine::funder).containsExactly(PaybackFunder.SELLER);
        assertThat(PaybackSettlement.settle(lines, List.of(admin(BALL, 5, 500L, larger), seller(BALL, 3, 500L)))
                .payouts()).extracting(PayoutLine::funder).containsExactly(PaybackFunder.ADMIN);
    }

    @Test
    @DisplayName("겹침 모드는 상품마다 그 상품 어드민 조건의 모드를 따르고, ONLY 모드는 고른 쪽 미달이면 지급하지 않는다")
    void overlapOnlyPerProduct() {
        List<ConfirmedLine> lines = List.of(new ConfirmedLine(10L, BALL, 5), new ConfirmedLine(10L, SHOE, 5));
        List<PaybackConditionSnapshot> conditions = List.of(
                admin(BALL, 6, 500L, PaybackOverlapPolicy.ADMIN_ONLY), seller(BALL, 3, 300L),
                admin(SHOE, 3, 200L, PaybackOverlapPolicy.SELLER_ONLY), seller(SHOE, 3, 100L));

        assertThat(PaybackSettlement.settle(lines, conditions).payouts())
                .extracting(PayoutLine::productId, PayoutLine::funder, PayoutLine::sellerId)
                .containsExactly(tuple(SHOE, PaybackFunder.SELLER, SELLER));
    }

    @Test
    @DisplayName("판정 결과는 조건별 기록을 남기고, 적용된 조건이 하나라도 있으면 지급으로 표시하며 두 번 판정하지 않는다")
    void judgmentRecord() {
        PaybackSettlement.Result result = PaybackSettlement.settle(List.of(new ConfirmedLine(10L, BALL, 5)),
                List.of(admin(BALL, 6, 500L, null), seller(BALL, 3, 300L)));
        IndividualGroupPurchasePayback payback = new IndividualGroupPurchasePayback();

        payback.judge(result, LocalDateTime.of(2026, 11, 20, 3, 0));

        assertThat(payback.isPaid()).isTrue();
        assertThat(payback.isJudged()).isTrue();
        assertThat(payback.getConditionJudgments())
                .extracting(PaybackConditionJudgment::getFunder, PaybackConditionJudgment::isReached,
                        PaybackConditionJudgment::isApplied)
                .containsExactly(tuple(PaybackFunder.ADMIN, false, false), tuple(PaybackFunder.SELLER, true, true));
        assertThat(payback.isJudgeable(LocalDateTime.of(2027, 1, 1, 0, 0))).isFalse();
    }

    private static PaybackConditionSnapshot admin(Long productId, int minQuantity, long amountPerUnit,
                                                  PaybackOverlapPolicy policy) {
        return PaybackConditionSnapshot.of(productId, PaybackFunder.ADMIN, null, minQuantity, amountPerUnit, policy);
    }

    private static PaybackConditionSnapshot seller(Long productId, int minQuantity, long amountPerUnit) {
        return PaybackConditionSnapshot.of(productId, PaybackFunder.SELLER, SELLER, minQuantity, amountPerUnit, null);
    }

    private static PaybackOrder paid(Long orderId, Long buyer, PaybackOrderItem... items) {
        PaybackOrder order = new PaybackOrder(orderId);
        order.applyPlaced(5L, buyer, List.of(items), OrderStatus.PAID);
        return order;
    }

    private static PaybackOrderItem item(Long orderItemId, Long productId, Long sellerId, int quantity) {
        return PaybackOrderItem.of(orderItemId, productId, sellerId, quantity);
    }
}
