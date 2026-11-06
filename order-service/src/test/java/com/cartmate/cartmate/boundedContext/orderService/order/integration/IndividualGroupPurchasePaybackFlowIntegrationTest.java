package com.cartmate.cartmate.boundedContext.orderService.order.integration;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching.DemandGroupPurchaseOpenRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.DeliveryAddressRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.IndividualGroupOrderCreateRequest;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import com.cartmate.cartmate.support.ConcurrentRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.cartmate.cartmate.support.OutboxRelayHelper.relayUntil;
import static org.assertj.core.api.Assertions.assertThat;

@CartMateIntegrationTest
class IndividualGroupPurchasePaybackFlowIntegrationTest {

    private static final Long PRODUCT_ID = 2L;
    private static final Long INVENTORY_ID = 4L;
    private static final int CONCURRENT_ROUNDS = 10;

    @Autowired
    private IndividualGroupPurchaseFacade individualGroupPurchaseFacade;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final long base = 8_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;
    private final Long admin = base;
    private long sequence;

    @Test
    @DisplayName("어드민 공동구매를 열고 주문 · 결제 · 종료한 뒤 모든 주문이 구매확정되면 판정이 한 번 저장되고 달성한 구매자 모두에게 지급을 요청한다")
    void judgeOnLastConfirmationAfterClose() {
        Long purchaseId = openByAdmin(2, 3_000L);
        relayUntil(jdbcTemplate, outboxRelay, () -> replicaHasPaybackCondition(purchaseId), "사본에 페이백 조건이 채워지지 않았다");
        List<Long> orderIds = List.of(paidOrder(nextBuyer(), purchaseId), paidOrder(nextBuyer(), purchaseId),
                paidOrder(nextBuyer(), purchaseId));

        individualGroupPurchaseFacade.closeByAdmin(admin, purchaseId);
        relayUntilClosedPublished(purchaseId);

        assertThat(replica(purchaseId).get("closed")).isEqualTo(true);
        assertThat(paybackCount(purchaseId)).as("구매확정 전에는 판정하지 않는다").isZero();

        orderFacade.recordPurchaseConfirmed(orderIds.get(0), LocalDateTime.now());
        orderFacade.recordPurchaseConfirmed(orderIds.get(1), LocalDateTime.now());
        assertThat(paybackCount(purchaseId)).as("확정되지 않은 주문이 남아 있으면 판정하지 않는다").isZero();

        orderFacade.recordPurchaseConfirmed(orderIds.get(2), LocalDateTime.now());

        assertThat(paybackCount(purchaseId)).isEqualTo(1);
        Map<String, Object> payback = payback(purchaseId);
        assertThat(((Number) payback.get("min_buyers")).intValue()).isEqualTo(2);
        assertThat(((Number) payback.get("payback_amount")).longValue()).isEqualTo(3_000L);
        assertThat(((Number) payback.get("buyer_count")).intValue()).isEqualTo(3);
        assertThat(payback.get("reached")).isEqualTo(true);
        assertThat(payoutCount(purchaseId)).isEqualTo(3);
        assertThat(confirmationCount(orderIds)).isEqualTo(3);
    }

    @Test
    @DisplayName("종료 전에 모든 주문이 이미 구매확정 · 취소됐으면 종료 이벤트 처리만으로 판정이 나고, 취소한 구매자는 세지 않는다")
    void judgeOnCloseWhenAllOrdersSettled() {
        Long purchaseId = openByAdmin(5, 1_000L);
        relayUntil(jdbcTemplate, outboxRelay, () -> replicaHasPaybackCondition(purchaseId), "사본에 페이백 조건이 채워지지 않았다");
        Long confirmedA = paidOrder(nextBuyer(), purchaseId);
        Long confirmedB = paidOrder(nextBuyer(), purchaseId);
        Long canceledBuyer = nextBuyer();
        Long canceled = createOrder(canceledBuyer, purchaseId);
        orderFacade.cancelByBuyer(canceledBuyer, canceled);

        orderFacade.recordPurchaseConfirmed(confirmedA, LocalDateTime.now());
        orderFacade.recordPurchaseConfirmed(confirmedB, LocalDateTime.now());
        relayUntil(jdbcTemplate, outboxRelay, () -> outboxPending() == 0, "남은 아웃박스 이벤트가 릴레이되지 않았다");
        assertThat(paybackCount(purchaseId)).as("판매 중에는 판정하지 않는다").isZero();

        individualGroupPurchaseFacade.closeByAdmin(admin, purchaseId);
        relayUntilClosedPublished(purchaseId);

        assertThat(paybackCount(purchaseId)).isEqualTo(1);
        Map<String, Object> payback = payback(purchaseId);
        assertThat(((Number) payback.get("min_buyers")).intValue()).isEqualTo(5);
        assertThat(((Number) payback.get("buyer_count")).intValue()).isEqualTo(2);
        assertThat(payback.get("reached")).isEqualTo(false);
        assertThat(payoutCount(purchaseId)).isZero();
        assertThat(orderStatus(canceled)).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("같은 공동구매의 마지막 두 주문 구매확정이 동시에 와도 사본 행 락으로 판정은 정확히 한 번만 저장된다")
    void concurrentLastConfirmationsJudgeOnce() throws InterruptedException {
        for (int round = 0; round < CONCURRENT_ROUNDS; round++) {
            Long purchaseId = openByAdmin(3, 2_000L);
            relayUntil(jdbcTemplate, outboxRelay, () -> replicaHasPaybackCondition(purchaseId), "사본에 페이백 조건이 채워지지 않았다");
            Long first = paidOrder(nextBuyer(), purchaseId);
            Long lastA = paidOrder(nextBuyer(), purchaseId);
            Long lastB = paidOrder(nextBuyer(), purchaseId);
            individualGroupPurchaseFacade.closeByAdmin(admin, purchaseId);
            relayUntilClosedPublished(purchaseId);
            orderFacade.recordPurchaseConfirmed(first, LocalDateTime.now());
            assertThat(paybackCount(purchaseId)).isZero();

            ConcurrentRunner.run(
                    () -> orderFacade.recordPurchaseConfirmed(lastA, LocalDateTime.now()),
                    () -> orderFacade.recordPurchaseConfirmed(lastB, LocalDateTime.now())
            ).assertNoUnexpected();

            assertThat(paybackCount(purchaseId)).as("라운드 %d 판정 행 수", round).isEqualTo(1);
            Map<String, Object> payback = payback(purchaseId);
            assertThat(((Number) payback.get("buyer_count")).intValue()).as("라운드 %d 구매자 수", round).isEqualTo(3);
            assertThat(payback.get("reached")).isEqualTo(true);
            assertThat(payoutCount(purchaseId)).as("라운드 %d 지급 요청 수", round).isEqualTo(3);
            assertThat(confirmationCount(List.of(first, lastA, lastB))).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("종료 이벤트가 개설 이벤트보다 먼저 전달돼도 닫힌 사본에 페이백 조건이 채워지고, 늦게 온 개설 이벤트는 사본을 다시 열지 않으며 판정이 맞게 난다")
    void closedBeforeOpenedStillFillsPaybackCondition() {
        Long purchaseId = openByAdmin(2, 5_000L);
        delayOpenedEvent(purchaseId);
        Long orderA = paidOrder(nextBuyer(), purchaseId);
        Long orderB = paidOrder(nextBuyer(), purchaseId);
        assertThat(replicaCount(purchaseId)).as("개설 이벤트가 아직 전달되지 않아 사본이 없다").isZero();

        individualGroupPurchaseFacade.closeByAdmin(admin, purchaseId);
        relayUntilClosedPublished(purchaseId);

        assertThat(eventStatus(purchaseId, "IndividualGroupPurchaseOpenedEvent")).isEqualTo("NEW");
        Map<String, Object> closedReplica = replica(purchaseId);
        assertThat(closedReplica.get("closed")).isEqualTo(true);
        assertThat(((Number) closedReplica.get("min_quantity")).intValue()).isEqualTo(2);
        assertThat(((Number) closedReplica.get("amount_per_unit")).longValue()).isEqualTo(5_000L);
        assertThat(paybackCount(purchaseId)).isZero();

        releaseOpenedEvent(purchaseId);
        relayUntil(jdbcTemplate, outboxRelay,
                () -> "PUBLISHED".equals(eventStatus(purchaseId, "IndividualGroupPurchaseOpenedEvent")),
                "늦게 온 개설 이벤트가 릴레이되지 않았다");

        Map<String, Object> afterOpened = replica(purchaseId);
        assertThat(afterOpened.get("closed")).isEqualTo(true);
        assertThat(((Number) afterOpened.get("min_quantity")).intValue()).isEqualTo(2);
        assertThat(((Number) afterOpened.get("amount_per_unit")).longValue()).isEqualTo(5_000L);

        orderFacade.recordPurchaseConfirmed(orderA, LocalDateTime.now());
        orderFacade.recordPurchaseConfirmed(orderB, LocalDateTime.now());

        assertThat(paybackCount(purchaseId)).isEqualTo(1);
        Map<String, Object> payback = payback(purchaseId);
        assertThat(((Number) payback.get("min_buyers")).intValue()).isEqualTo(2);
        assertThat(((Number) payback.get("payback_amount")).longValue()).isEqualTo(5_000L);
        assertThat(((Number) payback.get("buyer_count")).intValue()).isEqualTo(2);
        assertThat(payback.get("reached")).isEqualTo(true);
        assertThat(payoutCount(purchaseId)).isEqualTo(2);
    }

    private Long openByAdmin(int paybackMinBuyers, long paybackAmount) {
        return individualGroupPurchaseFacade.openFromDemand(admin, PRODUCT_ID, new DemandGroupPurchaseOpenRequest(
                "페이백 판정 흐름", LocalDateTime.now().plusDays(1), paybackMinBuyers, paybackAmount, null)).id();
    }

    private Long nextBuyer() {
        return base + (++sequence);
    }

    private Long createOrder(Long buyer, Long purchaseId) {
        return orderFacade.createIndividualGroup(buyer, new IndividualGroupOrderCreateRequest(purchaseId,
                List.of(new IndividualGroupOrderCreateRequest.Item(INVENTORY_ID, 1)),
                new DeliveryAddressRequest("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"))).orderId();
    }

    private Long paidOrder(Long buyer, Long purchaseId) {
        Long orderId = createOrder(buyer, purchaseId);
        orderFacade.payOrder(buyer, orderId);
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        return orderId;
    }

    private void relayUntilClosedPublished(Long purchaseId) {
        relayUntil(jdbcTemplate, outboxRelay,
                () -> "PUBLISHED".equals(eventStatus(purchaseId, "IndividualGroupPurchaseClosedEvent")),
                "종료 이벤트가 릴레이되지 않았다: " + purchaseId);
    }

    private void delayOpenedEvent(Long purchaseId) {
        jdbcTemplate.update("UPDATE outbox_event SET next_attempt_at = ? WHERE aggregate_type = 'IndividualGroupPurchase' "
                        + "AND aggregate_id = ? AND event_type = 'IndividualGroupPurchaseOpenedEvent'",
                LocalDateTime.now().plusHours(1), String.valueOf(purchaseId));
    }

    private void releaseOpenedEvent(Long purchaseId) {
        jdbcTemplate.update("UPDATE outbox_event SET next_attempt_at = ? WHERE aggregate_type = 'IndividualGroupPurchase' "
                        + "AND aggregate_id = ? AND event_type = 'IndividualGroupPurchaseOpenedEvent'",
                LocalDateTime.now().minusMinutes(1), String.valueOf(purchaseId));
    }

    private String eventStatus(Long purchaseId, String eventType) {
        return jdbcTemplate.queryForObject("SELECT status FROM outbox_event WHERE aggregate_type = 'IndividualGroupPurchase' "
                + "AND aggregate_id = ? AND event_type = ?", String.class, String.valueOf(purchaseId), eventType);
    }

    private int outboxPending() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE status = 'NEW' AND next_attempt_at <= ?",
                Integer.class, LocalDateTime.now());
    }

    private int replicaCount(Long purchaseId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM individual_group_purchase_replica WHERE individual_group_purchase_id = ?",
                Integer.class, purchaseId);
    }

    private boolean replicaHasPaybackCondition(Long purchaseId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM individual_group_purchase_replica_payback_condition "
                + "WHERE individual_group_purchase_id = ? AND funder = 'ADMIN'",
                Integer.class, purchaseId) == 1;
    }

    private Map<String, Object> replica(Long purchaseId) {
        return jdbcTemplate.queryForMap("SELECT r.closed, c.min_quantity, c.amount_per_unit "
                + "FROM individual_group_purchase_replica r "
                + "JOIN individual_group_purchase_replica_payback_condition c "
                + "ON c.individual_group_purchase_id = r.individual_group_purchase_id AND c.funder = 'ADMIN' "
                + "WHERE r.individual_group_purchase_id = ?", purchaseId);
    }

    private int paybackCount(Long purchaseId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM individual_group_purchase_payback WHERE individual_group_purchase_id = ?",
                Integer.class, purchaseId);
    }

    private Map<String, Object> payback(Long purchaseId) {
        return jdbcTemplate.queryForMap("SELECT min_buyers, payback_amount, buyer_count, reached "
                + "FROM individual_group_purchase_payback WHERE individual_group_purchase_id = ?", purchaseId);
    }

    private int payoutCount(Long purchaseId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM individual_group_purchase_payback_payout WHERE individual_group_purchase_id = ?",
                Integer.class, purchaseId);
    }

    private int confirmationCount(List<Long> orderIds) {
        return orderIds.stream()
                .mapToInt(orderId -> jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM order_purchase_confirmation WHERE order_id = ?", Integer.class, orderId))
                .sum();
    }

    private String orderStatus(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }
}
