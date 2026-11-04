package com.cartmate.cartmate.boundedContext.order.integration;

import com.cartmate.cartmate.boundedContext.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.order.dto.personal.DeliveryAddressRequest;
import com.cartmate.cartmate.boundedContext.order.dto.personal.IndividualGroupOrderCreateRequest;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.personal.OrderRepository;
import com.cartmate.cartmate.boundedContext.order.support.TestDataCleaner;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.outbox.ManualOutboxRelayConfig;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * cart 개인물품 공동구매 열림 · 갱신 · 종료 → 아웃박스 → order 사본 → 주문 생성 · 결제 판단 흐름(ARCH-16).
 * 릴레이를 직접 돌려 사본이 늦는 순간을 만든다.
 */
@SpringBootTest(properties = {"cartmate.scheduling.enabled=false", "custom.outbox.relay.initial-delay=PT1H"})
@ActiveProfiles("test")
@Import(ManualOutboxRelayConfig.class)
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
    @DisplayName("종료 전에 만든 주문은 종료가 사본에 반영된 뒤에도 결제할 수 있고, 사본이 늦어 종료 뒤에 만든 주문은 결제가 막힌다")
    void paymentRecheckCatchesOrdersCreatedWhileReplicaWasStale() {
        Long purchaseId = openPurchase(List.of(1L, 2L));
        relayUntil(() -> replicaRevision(purchaseId) == 0);
        Long orderedBeforeClose = createOrder(purchaseId);

        individualGroupPurchaseFacade.close(creator, purchaseId);
        // 종료 이벤트가 아직 릴레이 전이라 사본은 열려 있다 → 다른 회원의 주문이 만들어진다
        Long lateBuyer = buyer + 1;
        Long orderedAfterClose = orderFacade.createIndividualGroup(lateBuyer, request(purchaseId)).orderId();
        orderIds.add(orderedAfterClose);

        relayUntil(() -> Boolean.TRUE.equals(replicaRow(purchaseId).get("closed")));

        LocalDateTime now = LocalDateTime.now();
        assertThat(orderFacade.getPaymentInfo(orderedBeforeClose, buyer, now).payable()).isTrue();
        assertThat(orderFacade.getPaymentInfo(orderedAfterClose, lateBuyer, now).payable()).isFalse();
        assertThatThrownBy(() -> orderFacade.applyPayment(orderedAfterClose, lateBuyer, 1L, now))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_GROUP_PURCHASE_NOT_SOLD_AT_ORDER.getMsg());
    }

    private Long openPurchase(List<Long> productIds) {
        Long id = individualGroupPurchaseFacade.create(creator,
                new IndividualGroupPurchaseCreateRequest("사본 흐름", productIds)).id();
        purchaseIds.add(id);
        return id;
    }

    private Long createOrder(Long purchaseId) {
        Long orderId = orderFacade.createIndividualGroup(buyer, request(purchaseId)).orderId();
        orderIds.add(orderId);
        return orderId;
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
