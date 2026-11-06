package com.cartmate.cartmate.boundedContext.orderService.order.integration;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchaseStatus;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.groupshared.SharedGroupOrderDetailResponse;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.groupshared.SharedGroupOrderSummaryResponse;
import com.cartmate.cartmate.boundedContext.orderService.order.out.FakeOrderProductClient;
import com.cartmate.cartmate.boundedContext.orderService.order.support.TestDataCleaner;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import com.cartmate.cartmate.global.outbox.ManualOutboxRelayConfig;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공동구매 마감(cart 트랜잭션: CLOSED + 아웃박스)과 공동 주문 생성(order consumer 트랜잭션)이 분리된 흐름.
 * 릴레이를 직접 돌려 마감 이벤트 → 공동 주문 → 생성 소식 → ORDER_CREATED 순서로 넘어가는지 본다.
 */
@CartMateIntegrationTest
class SharedGroupOrderCreationFlowIntegrationTest {

    private static final String CLOSED_EVENT = "SharedGroupPurchaseClosedEvent";
    private static final String CREATED_EVENT = "SharedGroupOrderCreatedEvent";
    private static final String FAILED_EVENT = "SharedGroupOrderCreationFailedEvent";

    @Autowired
    private SharedGroupPurchaseFacade sharedGroupPurchaseFacade;

    @Autowired
    private SharedGroupPurchaseRepository sharedGroupPurchaseRepository;

    @Autowired
    private SharedGroupOrderFacade sharedGroupOrderFacade;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private FakeOrderProductClient fakeOrderProductClient;

    private final List<Long> purchaseIds = new ArrayList<>();

    private final long base = 8_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;
    private final Long creator = base;
    private final Long member = base + 1;

    @AfterEach
    void cleanUp() {
        fakeOrderProductClient.clearOverride();
        transactionTemplate.executeWithoutResult(status -> {
            purchaseIds.forEach(id -> {
                // 다른 테스트의 릴레이가 남은 행을 집어 지운 공동구매로 주문을 만들지 않게 이 공동구매의 이벤트를 모두 지운다
                jdbcTemplate.update("DELETE FROM outbox_event WHERE aggregate_type = 'SharedGroupPurchase' AND aggregate_id = ?",
                        String.valueOf(id));
                orderIdOf(id).ifPresent(orderId -> jdbcTemplate.update(
                        "DELETE FROM outbox_event WHERE aggregate_type = 'SharedGroupOrder' AND aggregate_id = ?",
                        String.valueOf(orderId)));
            });
            new TestDataCleaner(jdbcTemplate).deleteSharedGroupOrdersOfPurchases(purchaseIds);
            purchaseIds.forEach(sharedGroupPurchaseRepository::deleteById);
        });
    }

    @Test
    @DisplayName("마감이 커밋되면 공동구매는 CLOSED, 마감 이벤트는 아웃박스에만 있고 공동 주문은 아직 없다. 릴레이 뒤 공동 주문 1건과 ORDER_CREATED, 참여자는 자기 부담 금액과 상세를 조회한다")
    void closeThenRelayCreatesOrder() {
        Long purchaseId = readyPurchase("마감 분리");

        sharedGroupPurchaseFacade.close(creator, purchaseId);

        assertThat(statusOf(purchaseId)).isEqualTo(SharedGroupPurchaseStatus.CLOSED);
        assertThat(outboxCount(purchaseId, CLOSED_EVENT)).isEqualTo(1);
        assertThat(orderCountOf(purchaseId)).isZero();

        relayUntil(() -> statusOf(purchaseId) == SharedGroupPurchaseStatus.ORDER_CREATED);

        assertThat(orderCountOf(purchaseId)).isEqualTo(1);
        Long orderId = orderIdOf(purchaseId).orElseThrow();
        assertThat(createdEventCount(orderId)).isEqualTo(1);

        SharedGroupOrderSummaryResponse summary = sharedGroupOrderFacade.getMyList(member).stream()
                .filter(order -> order.id().equals(orderId))
                .findFirst()
                .orElseThrow();
        assertThat(summary.totalAmount()).isEqualTo(75000L);
        assertThat(summary.myShareAmount()).isEqualTo(37500L);
        assertThat(summary.participantCount()).isEqualTo(2);

        SharedGroupOrderDetailResponse detail = sharedGroupOrderFacade.getDetail(creator, orderId);
        assertThat(detail.creator()).isTrue();
        assertThat(detail.items()).singleElement()
                .satisfies(item -> assertThat(item.unitPrice()).isEqualTo(25000L));
    }

    @Test
    @DisplayName("같은 마감 이벤트가 다시 전달돼도 공동 주문은 1건이고 공동구매는 ORDER_CREATED 그대로다")
    void duplicateDeliveryCreatesOneOrder() {
        Long purchaseId = readyPurchase("중복 전달");
        sharedGroupPurchaseFacade.close(creator, purchaseId);
        relayUntil(() -> statusOf(purchaseId) == SharedGroupPurchaseStatus.ORDER_CREATED);

        // at-least-once: 싱크 성공 뒤 PUBLISHED 기록이 실패하면 같은 행이 다시 간다
        jdbcTemplate.update("UPDATE outbox_event SET status = 'NEW', next_attempt_at = ? " +
                "WHERE aggregate_type = 'SharedGroupPurchase' AND aggregate_id = ? AND event_type = ?",
                LocalDateTime.now(), String.valueOf(purchaseId), CLOSED_EVENT);
        relayUntil(() -> pendingOf(purchaseId) == 0);

        assertThat(orderCountOf(purchaseId)).isEqualTo(1);
        assertThat(statusOf(purchaseId)).isEqualTo(SharedGroupPurchaseStatus.ORDER_CREATED);
        Long orderId = orderIdOf(purchaseId).orElseThrow();
        relayUntil(() -> pendingOfOrder(orderId) == 0);
        assertThat(statusOf(purchaseId)).isEqualTo(SharedGroupPurchaseStatus.ORDER_CREATED);
    }

    @Test
    @DisplayName("공동 주문을 곧바로 만들 수 없으면(상품을 찾을 수 없음) 실패 소식으로 FROZEN에 돌아가고, 다시 마감하면 주문이 만들어진다")
    void immediateFailureRevertsToFrozen() {
        Long purchaseId = readyPurchase("즉시 실패");
        sharedGroupPurchaseFacade.close(creator, purchaseId);
        fakeOrderProductClient.overrideFindAll(inventoryIds -> List.of());

        relayUntil(() -> statusOf(purchaseId) == SharedGroupPurchaseStatus.FROZEN);

        assertThat(orderCountOf(purchaseId)).isZero();
        assertThat(outboxCount(purchaseId, FAILED_EVENT)).isEqualTo(1);
        assertThat(outboxStatus(purchaseId, CLOSED_EVENT)).containsExactly("PUBLISHED");
        SharedGroupPurchase frozen = sharedGroupPurchaseRepository.findById(purchaseId).orElseThrow();
        assertThat(frozen.getFrozenAt()).isNotNull();

        fakeOrderProductClient.clearOverride();
        sharedGroupPurchaseFacade.close(creator, purchaseId);
        relayUntil(() -> statusOf(purchaseId) == SharedGroupPurchaseStatus.ORDER_CREATED);
        assertThat(orderCountOf(purchaseId)).isEqualTo(1);
    }

    @Test
    @DisplayName("다시 하면 될 수 있는 실패(DB 순단 등)는 공동구매를 CLOSED로 두고 마감 이벤트를 재시도 대기로 남긴다")
    void transientFailureKeepsClosedAndRetries() {
        Long purchaseId = readyPurchase("재시도 실패");
        sharedGroupPurchaseFacade.close(creator, purchaseId);
        fakeOrderProductClient.overrideFindAll(inventoryIds -> {
            throw new QueryTimeoutException("순단");
        });

        relayUntil(() -> attemptsOf(purchaseId, CLOSED_EVENT) >= 1);

        assertThat(statusOf(purchaseId)).isEqualTo(SharedGroupPurchaseStatus.CLOSED);
        assertThat(orderCountOf(purchaseId)).isZero();
        assertThat(outboxStatus(purchaseId, CLOSED_EVENT)).containsExactly("NEW");
        assertThat(outboxCount(purchaseId, FAILED_EVENT)).isZero();
    }

    @Test
    @DisplayName("마감 뒤 제한 시간 안에 공동 주문 생성 소식이 없으면 FROZEN으로 돌아가고, 늦게 온 생성 소식은 그래도 받는다")
    void orderCreationTimeoutRevertsToFrozen() {
        Long purchaseId = readyPurchase("시간 초과");
        sharedGroupPurchaseFacade.close(creator, purchaseId);
        // 다른 테스트 컨텍스트의 릴레이가 먼저 집어 가지 않게 마감 이벤트를 잠시 비켜 둔다(consumer가 계속 실패 중인 상황)
        // 시각은 DB NOW()가 아니라 JVM 시각으로 넣는다(앱이 LocalDateTime.now()와 비교하므로 시간대 차이를 피한다)
        jdbcTemplate.update("UPDATE outbox_event SET next_attempt_at = ? " +
                "WHERE aggregate_type = 'SharedGroupPurchase' AND aggregate_id = ?",
                LocalDateTime.now().plusHours(1), String.valueOf(purchaseId));

        // 스케줄러가 하는 일(대상 조회 → 하나씩 복귀)을 직접 부른다. 스케줄러 자체는 ShedLock 최소 유지 시간 때문에 연달아 부를 수 없다
        assertThat(sharedGroupPurchaseFacade.findIdsAwaitingOrderTooLong(LocalDateTime.now())).doesNotContain(purchaseId);
        sharedGroupPurchaseFacade.revertByOrderCreationTimeout(purchaseId);
        assertThat(statusOf(purchaseId)).isEqualTo(SharedGroupPurchaseStatus.CLOSED);

        jdbcTemplate.update("UPDATE shared_group_purchase SET closed_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(11), purchaseId);
        assertThat(sharedGroupPurchaseFacade.findIdsAwaitingOrderTooLong(LocalDateTime.now())).contains(purchaseId);
        sharedGroupPurchaseFacade.revertByOrderCreationTimeout(purchaseId);

        assertThat(statusOf(purchaseId)).isEqualTo(SharedGroupPurchaseStatus.FROZEN);
        assertThat(sharedGroupPurchaseRepository.findById(purchaseId).orElseThrow().getFrozenAt()).isNotNull();
        assertThat(orderCountOf(purchaseId)).isZero();

        // 주문은 order 생성 창(마감 뒤 8분) 안에 만들어졌는데 생성 소식이 늦게 온 경우: 주문 쪽이 기준이다
        jdbcTemplate.update("UPDATE outbox_event SET next_attempt_at = ? " +
                "WHERE aggregate_type = 'SharedGroupPurchase' AND aggregate_id = ?",
                LocalDateTime.now(), String.valueOf(purchaseId));
        relayUntil(() -> statusOf(purchaseId) == SharedGroupPurchaseStatus.ORDER_CREATED);
        assertThat(orderCountOf(purchaseId)).isEqualTo(1);
    }

    @Test
    @DisplayName("마감 트랜잭션이 롤백되면 CLOSED도 아웃박스 행도 남지 않고, 릴레이를 돌려도 공동 주문이 없다")
    void closeRollbackLeavesNothing() {
        Long purchaseId = readyPurchase("마감 롤백");

        transactionTemplate.executeWithoutResult(status -> {
            sharedGroupPurchaseFacade.close(creator, purchaseId);
            status.setRollbackOnly();
        });

        assertThat(statusOf(purchaseId)).isEqualTo(SharedGroupPurchaseStatus.OPEN);
        assertThat(outboxCount(purchaseId, CLOSED_EVENT)).isZero();
        ManualOutboxRelayConfig.releaseRelayLock(jdbcTemplate);
        outboxRelay.relay();
        assertThat(orderCountOf(purchaseId)).isZero();
    }

    private Long readyPurchase(String title) {
        var created = sharedGroupPurchaseFacade.create(creator, new SharedGroupPurchaseCreateRequest(title, 5));
        purchaseIds.add(created.id());
        sharedGroupPurchaseFacade.join(member, created.inviteCode());
        sharedGroupPurchaseFacade.addItem(member, created.id(), 4L, 3);
        return created.id();
    }

    // 같은 DB를 쓰는 다른 테스트가 남긴 NEW 행이 앞에 있을 수 있어 조건이 맞을 때까지 돌린다
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

    private SharedGroupPurchaseStatus statusOf(Long purchaseId) {
        return SharedGroupPurchaseStatus.valueOf(jdbcTemplate.queryForObject(
                "SELECT status FROM shared_group_purchase WHERE id = ?", String.class, purchaseId));
    }

    private int orderCountOf(Long purchaseId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shared_group_order WHERE shared_group_purchase_id = ?", Integer.class, purchaseId);
    }

    private Optional<Long> orderIdOf(Long purchaseId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM shared_group_order WHERE shared_group_purchase_id = ?", Long.class, purchaseId)
                .stream().findFirst();
    }

    private int outboxCount(Long purchaseId, String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_type = 'SharedGroupPurchase' AND aggregate_id = ? AND event_type = ?",
                Integer.class, String.valueOf(purchaseId), eventType);
    }

    private List<String> outboxStatus(Long purchaseId, String eventType) {
        return jdbcTemplate.queryForList(
                "SELECT status FROM outbox_event WHERE aggregate_type = 'SharedGroupPurchase' AND aggregate_id = ? AND event_type = ?",
                String.class, String.valueOf(purchaseId), eventType);
    }

    private int attemptsOf(Long purchaseId, String eventType) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT attempts FROM outbox_event WHERE aggregate_type = 'SharedGroupPurchase' AND aggregate_id = ? AND event_type = ?",
                String.valueOf(purchaseId), eventType);
        return rows.isEmpty() ? 0 : ((Number) rows.getFirst().get("attempts")).intValue();
    }

    private int pendingOf(Long purchaseId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_type = 'SharedGroupPurchase' AND aggregate_id = ? AND status = 'NEW'",
                Integer.class, String.valueOf(purchaseId));
    }

    private int pendingOfOrder(Long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_type = 'SharedGroupOrder' AND aggregate_id = ? AND status = 'NEW'",
                Integer.class, String.valueOf(orderId));
    }

    private int createdEventCount(Long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_type = 'SharedGroupOrder' AND aggregate_id = ? AND event_type = ?",
                Integer.class, String.valueOf(orderId), CREATED_EVENT);
    }
}
