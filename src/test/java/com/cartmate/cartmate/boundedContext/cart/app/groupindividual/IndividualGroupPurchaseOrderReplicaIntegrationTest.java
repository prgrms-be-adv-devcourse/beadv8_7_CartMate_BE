package com.cartmate.cartmate.boundedContext.cart.app.groupindividual;

import com.cartmate.cartmate.support.CartMateIntegrationTest;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderLine;
import com.cartmate.cartmate.boundedContext.cart.out.groupindividual.IndividualGroupPurchaseOrderReplicaRepository;
import com.cartmate.cartmate.shared.event.DomainEvent;
import com.cartmate.cartmate.shared.event.EventEnvelope;
import com.cartmate.cartmate.shared.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.order.dto.DeliveryAddressInfo;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderCreatedEvent;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** 아웃박스 릴레이가 넘기는 래퍼 이벤트(트랜잭션 밖)를 받아 cart 주문 읽기 모델과 현황판 조회가 맞게 바뀌는지 본다. */
@CartMateIntegrationTest
class IndividualGroupPurchaseOrderReplicaIntegrationTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 2, 12, 0, 0);
    private static final DeliveryAddressInfo ADDRESS = new DeliveryAddressInfo("홍길동", "010-1234-5678", "12345", "서울", "101호");

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private IndividualGroupPurchaseOrderReplicaRepository replicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final long base = 7_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 10;
    private final List<Long> orderIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        orderIds.forEach(orderId -> {
            jdbcTemplate.update("DELETE FROM individual_group_purchase_order_line WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM individual_group_purchase_order WHERE order_id = ?", orderId);
        });
    }

    @Test
    @DisplayName("생성 · 결제 이벤트를 받으면 현황판에 결제 완료 주문 줄이 주문 시각과 함께 나오고, 취소되면 빠진다")
    void createdPaidCanceled() {
        Long purchaseId = base;
        Long orderId = newOrderId();

        relay(created(orderId, purchaseId, "INDIVIDUAL_GROUP"));
        assertThat(replicaRepository.findPaidLines(purchaseId)).isEmpty();

        relay(paid(orderId, purchaseId, "INDIVIDUAL_GROUP"));
        assertThat(replicaRepository.findPaidLines(purchaseId))
                .extracting(IndividualGroupPurchaseOrderLine::orderId, IndividualGroupPurchaseOrderLine::productId,
                        IndividualGroupPurchaseOrderLine::quantity, IndividualGroupPurchaseOrderLine::orderedAt)
                .containsExactly(tuple(orderId, 7L, 2, CREATED_AT), tuple(orderId, 8L, 1, CREATED_AT));

        relay(new OrderCanceledEvent(orderId, 1L, "INDIVIDUAL_GROUP", "BUYER_CANCELED", true, CREATED_AT.plusHours(1)));
        assertThat(replicaRepository.findPaidLines(purchaseId)).isEmpty();
    }

    @Test
    @DisplayName("같은 이벤트가 다시 와도 줄이 늘지 않고, 취소가 먼저 와도 늦게 온 결제가 현황판에 나오지 않는다")
    void duplicatesAndReordering() {
        Long purchaseId = base + 1;
        Long duplicated = newOrderId();
        Long canceledFirst = newOrderId();

        relay(paid(duplicated, purchaseId, "INDIVIDUAL_GROUP"));
        relay(paid(duplicated, purchaseId, "INDIVIDUAL_GROUP"));
        relay(created(duplicated, purchaseId, "INDIVIDUAL_GROUP"));

        relay(new OrderCanceledEvent(canceledFirst, 1L, "INDIVIDUAL_GROUP", "BUYER_CANCELED", true, CREATED_AT));
        relay(paid(canceledFirst, purchaseId, "INDIVIDUAL_GROUP"));

        assertThat(replicaRepository.findPaidLines(purchaseId))
                .extracting(IndividualGroupPurchaseOrderLine::orderId, IndividualGroupPurchaseOrderLine::orderedAt)
                .containsExactly(tuple(duplicated, CREATED_AT), tuple(duplicated, CREATED_AT));
    }

    @Test
    @DisplayName("판매 중지로 한 판매자 항목이 취소되면 그 줄만 빠지고, 모든 항목이 취소되면 주문 취소 이벤트 없이도 현황판에서 빠진다")
    void sellerItemsCanceled() {
        Long purchaseId = base + 3;
        Long orderId = newOrderId();
        relay(created(orderId, purchaseId, "INDIVIDUAL_GROUP"));
        relay(paid(orderId, purchaseId, "INDIVIDUAL_GROUP"));

        relay(new OrderSellerItemsCanceledEvent(orderId, 1L, 3L, 50000L, CREATED_AT.plusHours(1)));
        assertThat(replicaRepository.findPaidLines(purchaseId))
                .extracting(IndividualGroupPurchaseOrderLine::productId).containsExactly(8L);

        relay(new OrderSellerItemsCanceledEvent(orderId, 1L, 4L, 15000L, CREATED_AT.plusHours(1)));
        assertThat(replicaRepository.findPaidLines(purchaseId)).isEmpty();
    }

    @Test
    @DisplayName("사본이 없는 주문(개인 주문)의 판매자 항목 취소는 그냥 지나간다")
    void sellerItemsCanceledOfPersonalOrderIsIgnored() {
        Long orderId = newOrderId();

        relay(new OrderSellerItemsCanceledEvent(orderId, 1L, 3L, 50000L, CREATED_AT));

        assertThat(replicaRepository.findById(orderId)).isEmpty();
    }

    @Test
    @DisplayName("개인 주문 이벤트는 읽기 모델에 남기지 않는다")
    void personalOrdersAreIgnored() {
        Long orderId = newOrderId();

        relay(created(orderId, null, "PERSONAL"));
        relay(paid(orderId, null, "PERSONAL"));

        assertThat(replicaRepository.findById(orderId)).isEmpty();
    }

    @Test
    @DisplayName("읽기 모델 반영이 실패하면 예외가 릴레이(발행한 쪽)까지 올라간다. 그래야 릴레이가 실패로 기록하고 다시 시도한다")
    void failurePropagatesToRelay() {
        OrderPaidEvent broken = new OrderPaidEvent(newOrderId(), "ORD", 1L, "INDIVIDUAL_GROUP", base + 2,
                CREATED_AT, ADDRESS, null);

        assertThatThrownBy(() -> relay(broken)).isInstanceOf(NullPointerException.class);
    }

    private void relay(DomainEvent event) {
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID().toString(), event.getClass().getSimpleName(),
                "test", event.aggregateType(), event.aggregateId(), CREATED_AT, 1, "{}");
        applicationEventPublisher.publishEvent(new OutboxMessageRelayed(envelope, event));
    }

    private Long newOrderId() {
        Long orderId = base + 100 + orderIds.size();
        orderIds.add(orderId);
        return orderId;
    }

    private OrderCreatedEvent created(Long orderId, Long purchaseId, String orderType) {
        return new OrderCreatedEvent(orderId, "ORD-" + orderId, 1L, orderType, purchaseId, CREATED_AT, List.of(
                new OrderCreatedEvent.Item(orderId * 10, 7L, 70L, 3L, 2, 25000L),
                new OrderCreatedEvent.Item(orderId * 10 + 1, 8L, 80L, 4L, 1, 15000L)));
    }

    private OrderPaidEvent paid(Long orderId, Long purchaseId, String orderType) {
        return new OrderPaidEvent(orderId, "ORD-" + orderId, 1L, orderType, purchaseId, CREATED_AT.plusMinutes(5), ADDRESS,
                List.of(new OrderPaidEvent.Item(orderId * 10, 7L, 70L, 3L, "상품", null, 2, 25000L),
                        new OrderPaidEvent.Item(orderId * 10 + 1, 8L, 80L, 4L, "상품", null, 1, 15000L)));
    }
}
