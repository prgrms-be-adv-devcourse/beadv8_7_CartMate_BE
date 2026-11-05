package com.cartmate.cartmate.boundedContext.orderService.order.integration;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupindividual.IndividualGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.DeliveryAddressRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.IndividualGroupOrderCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.OrderRepository;
import com.cartmate.cartmate.boundedContext.orderService.order.support.TestDataCleaner;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.outbox.ManualOutboxRelayConfig;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import com.cartmate.cartmate.boundedContext.orderService.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * cart 개인물품 공동구매 열림 · 갱신 · 종료 → 아웃박스 → order 사본 → 주문 생성 · 결제 판단 흐름(ARCH-16).
 * 릴레이를 직접 돌려 사본이 늦는 순간을 만든다.
 */
@CartMateIntegrationTest
class IndividualGroupPurchaseReplicaFlowIntegrationTest {

    @Autowired
    private IndividualGroupPurchaseFacade individualGroupPurchaseFacade;

    @Autowired
    private IndividualGroupPurchaseRepository individualGroupPurchaseRepository;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> purchaseIds = new ArrayList<>();
    private final List<Long> orderIds = new ArrayList<>();

    private final long base = 7_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;
    private final Long creator = base;
    private final Long buyer = base + 1;

    @AfterEach
    void cleanUp() {
        purchaseIds.forEach(id -> jdbcTemplate.update(
                "DELETE FROM outbox_event WHERE aggregate_type = 'IndividualGroupPurchase' AND aggregate_id = ?",
                String.valueOf(id)));
        orderIds.forEach(orderId -> {
            jdbcTemplate.update("DELETE FROM outbox_event WHERE aggregate_type = 'Order' AND aggregate_id = ?",
                    String.valueOf(orderId));
            jdbcTemplate.update("DELETE FROM individual_group_purchase_order_line WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM individual_group_purchase_order WHERE order_id = ?", orderId);
        });
        new TestDataCleaner(jdbcTemplate).deleteDeliveriesOf(List.of(buyer, buyer + 1, buyer + 2, buyer + 3));
        orderIds.forEach(orderRepository::deleteById);
        new TestDataCleaner(jdbcTemplate).deleteIndividualGroupPurchaseReplicas(purchaseIds);
        purchaseIds.forEach(individualGroupPurchaseRepository::deleteById);
    }

    @Test
    @DisplayName("열림 · 상품 추가 · 마감 변경 · 종료가 릴레이를 거쳐 order 사본에 같은 순서의 번호로 반영된다")
    void replicaFollowsCart() {
        Long purchaseId = openPurchase(List.of(1L));
        relayUntil(() -> replicaRevision(purchaseId) == 0);
        assertThat(replicaProducts(purchaseId)).containsExactly(1L);

        individualGroupPurchaseFacade.addProduct(creator, purchaseId, 2L);
        LocalDateTime newDeadline = LocalDateTime.now().plusHours(5).truncatedTo(ChronoUnit.SECONDS);
        individualGroupPurchaseFacade.changeDeadline(creator, purchaseId, newDeadline);
        relayUntil(() -> replicaRevision(purchaseId) == 2);
        assertThat(replicaProducts(purchaseId)).containsExactlyInAnyOrder(1L, 2L);
        assertThat(replicaRow(purchaseId).get("deadline_at")).isEqualTo(newDeadline);

        individualGroupPurchaseFacade.close(creator, purchaseId);
        relayUntil(() -> Boolean.TRUE.equals(replicaRow(purchaseId).get("closed")));
        assertThat(replicaRevision(purchaseId)).isEqualTo(3);

        assertThatThrownBy(() -> orderFacade.createIndividualGroup(buyer, request(purchaseId)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_GROUP_PURCHASE_SALE_CLOSED.getMsg());
    }

    @Test
    @DisplayName("사본이 아직 없으면 cart에 잠그지 않고 물어 주문을 받는다 (개설 직후 · 배포 전부터 있던 공동구매)")
    void orderWithoutReplicaFallsBackToCart() {
        Long purchaseId = openPurchase(List.of(1L, 2L));
        assertThat(replicaCount(purchaseId)).isZero();

        Long orderId = createOrder(purchaseId);

        assertThat(orderFacade.getPaymentInfo(orderId, buyer, LocalDateTime.now()).payable()).isTrue();
        assertThat(replicaCount(purchaseId)).isZero();
    }

    @Test
    @DisplayName("종료가 사본에 닿으면 남은 결제 대기 주문은 판매 마감 사유로 취소되고 OrderCanceledEvent가 나간다. 결제 완료 주문 · 종료 전달 전 결제는 그대로")
    void closeCancelsPendingOrders() {
        Long purchaseId = openPurchase(List.of(1L, 2L));
        relayUntil(() -> replicaRevision(purchaseId) == 0);
        Long paidBeforeClose = createOrder(buyer, purchaseId);
        orderFacade.applyPayment(paidBeforeClose, buyer, paidBeforeClose, LocalDateTime.now());
        Long pendingBeforeClose = createOrder(buyer + 1, purchaseId);
        Long paidInWindow = createOrder(buyer + 2, purchaseId);

        individualGroupPurchaseFacade.close(creator, purchaseId);
        // 종료 이벤트가 아직 릴레이 전이라 사본은 열려 있다: 이 사이 결제는 통과하고(B안), 새 주문도 만들어진다
        orderFacade.applyPayment(paidInWindow, buyer + 2, paidInWindow, LocalDateTime.now());
        Long orderedInWindow = createOrder(buyer + 3, purchaseId);

        relayUntil(() -> "CANCELED".equals(orderStatus(pendingBeforeClose)) && "CANCELED".equals(orderStatus(orderedInWindow)));

        assertThat(orderStatus(paidBeforeClose)).isEqualTo("PAID");
        assertThat(orderStatus(paidInWindow)).isEqualTo("PAID");
        assertThat(cancelReason(pendingBeforeClose)).isEqualTo("GROUP_PURCHASE_CLOSED");
        assertThat(cancelReason(orderedInWindow)).isEqualTo("GROUP_PURCHASE_CLOSED");
        assertThat(canceledEventCount(pendingBeforeClose)).isEqualTo(1);
        assertThat(canceledEventCount(orderedInWindow)).isEqualTo(1);
        assertThat(orderFacade.getPaymentInfo(pendingBeforeClose, buyer + 1, LocalDateTime.now()).payable()).isFalse();
    }

    @Test
    @DisplayName("종료가 사본에 닿은 뒤에는 닫히기 전에 만든 주문도 결제를 거절한다 (자동 취소 전에 결제 요청이 먼저 와도)")
    void rejectPaymentAfterReplicaClosed() {
        Long purchaseId = openPurchase(List.of(1L, 2L));
        relayUntil(() -> replicaRevision(purchaseId) == 0);
        Long orderId = createOrder(buyer, purchaseId);
        individualGroupPurchaseFacade.close(creator, purchaseId);
        // 사본만 닫고(취소 consumer가 돌기 전 상태) 결제를 넣는다
        orderFacade.applyIndividualGroupPurchaseClosed(new IndividualGroupPurchaseClosedEvent(
                purchaseId, "MEMBER", "CREATOR", LocalDateTime.now().plusDays(1), LocalDateTime.now(), 1));

        assertThat(orderFacade.getPaymentInfo(orderId, buyer, LocalDateTime.now()).payable()).isFalse();
        assertThatThrownBy(() -> orderFacade.applyPayment(orderId, buyer, orderId, LocalDateTime.now()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_GROUP_PURCHASE_PAYMENT_CLOSED.getMsg());
        assertThat(orderStatus(orderId)).isEqualTo("PAYMENT_PENDING");
    }

    @Test
    @DisplayName("주문이 지휘하는 결제(payOrder)는 닫힌 공동구매 주문을 지갑 차감 전에 결제 불가로 거절한다")
    void payOrderRejectsBeforeChargeWhenClosed() {
        Long purchaseId = openPurchase(List.of(1L, 2L));
        relayUntil(() -> replicaRevision(purchaseId) == 0);
        Long orderId = createOrder(buyer, purchaseId);
        orderFacade.applyIndividualGroupPurchaseClosed(new IndividualGroupPurchaseClosedEvent(
                purchaseId, "MEMBER", "CREATOR", LocalDateTime.now().plusDays(1), LocalDateTime.now(), 1));

        assertThatThrownBy(() -> orderFacade.payOrder(buyer, orderId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NOT_PAYABLE.getMsg());
        assertThat(orderStatus(orderId)).isEqualTo("PAYMENT_PENDING");
    }

    @Test
    @DisplayName("결제 트랜잭션이 사본을 '열림'으로 읽은 뒤 사본이 닫혀 커밋되면, 같은 트랜잭션의 결제 반영은 옛 스냅샷이 아니라 최신 사본을 보고 거절한다")
    void applyPaymentRereadsReplicaWithLock() {
        Long purchaseId = openPurchase(List.of(1L, 2L));
        relayUntil(() -> replicaRevision(purchaseId) == 0);
        Long orderId = createOrder(buyer, purchaseId);

        AtomicReference<Throwable> paymentError = new AtomicReference<>();
        transactionTemplate.executeWithoutResult(status -> {
            // payment처럼 한 트랜잭션에서 결제 정보를 먼저 본다 → 스냅샷이 잡히고 사본은 열림
            assertThat(orderFacade.getPaymentInfo(orderId, buyer, LocalDateTime.now()).payable()).isTrue();
            CompletableFuture.runAsync(() -> orderFacade.applyIndividualGroupPurchaseClosed(
                    new IndividualGroupPurchaseClosedEvent(purchaseId, "MEMBER", "CREATOR",
                            LocalDateTime.now().plusDays(1), LocalDateTime.now(), 1))).join();
            try {
                orderFacade.applyPayment(orderId, buyer, orderId, LocalDateTime.now());
            } catch (RuntimeException e) {
                paymentError.set(e);
            }
            status.setRollbackOnly();
        });

        assertThat(paymentError.get())
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_GROUP_PURCHASE_PAYMENT_CLOSED.getMsg());
        assertThat(orderStatus(orderId)).isEqualTo("PAYMENT_PENDING");
    }

    private Long openPurchase(List<Long> productIds) {
        Long id = individualGroupPurchaseFacade.create(creator,
                new IndividualGroupPurchaseCreateRequest("사본 흐름", productIds)).id();
        purchaseIds.add(id);
        return id;
    }

    private Long createOrder(Long purchaseId) {
        return createOrder(buyer, purchaseId);
    }

    private Long createOrder(Long memberId, Long purchaseId) {
        Long orderId = orderFacade.createIndividualGroup(memberId, request(purchaseId)).orderId();
        orderIds.add(orderId);
        return orderId;
    }

    private String orderStatus(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private String cancelReason(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT cancel_reason FROM orders WHERE id = ?", String.class, orderId);
    }

    private int canceledEventCount(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE aggregate_type = 'Order' "
                + "AND aggregate_id = ? AND event_type = 'OrderCanceledEvent'", Integer.class, String.valueOf(orderId));
    }

    // 재고 4 = 상품 2(축구공)
    private static IndividualGroupOrderCreateRequest request(Long purchaseId) {
        return new IndividualGroupOrderCreateRequest(purchaseId,
                List.of(new IndividualGroupOrderCreateRequest.Item(4L, 1)),
                new DeliveryAddressRequest("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"));
    }

    private void relayUntil(BooleanSupplier condition) {
        for (int i = 0; i < 20; i++) {
            ManualOutboxRelayConfig.releaseRelayLock(jdbcTemplate);
            outboxRelay.relay();
            if (condition.getAsBoolean()) {
                return;
            }
        }
        throw new AssertionError("릴레이를 돌려도 기대한 상태가 되지 않았다");
    }

    private int replicaCount(Long purchaseId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM individual_group_purchase_replica WHERE individual_group_purchase_id = ?",
                Integer.class, purchaseId);
    }

    private long replicaRevision(Long purchaseId) {
        return replicaCount(purchaseId) == 0 ? -1 : jdbcTemplate.queryForObject(
                "SELECT revision FROM individual_group_purchase_replica WHERE individual_group_purchase_id = ?",
                Long.class, purchaseId);
    }

    private Map<String, Object> replicaRow(Long purchaseId) {
        return jdbcTemplate.queryForMap(
                "SELECT deadline_at, closed FROM individual_group_purchase_replica WHERE individual_group_purchase_id = ?",
                purchaseId);
    }

    private List<Long> replicaProducts(Long purchaseId) {
        return jdbcTemplate.queryForList("SELECT product_id FROM individual_group_purchase_replica_product "
                + "WHERE individual_group_purchase_id = ?", Long.class, purchaseId);
    }
}
