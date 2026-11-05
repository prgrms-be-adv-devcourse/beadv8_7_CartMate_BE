package com.cartmate.cartmate.boundedContext.order.out.personal;

import com.cartmate.cartmate.support.CartMateJpaTest;
import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order.OrderLine;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@CartMateJpaTest
class IndividualGroupPurchaseOrderQueryTest {

    private static final Long BUYER = 910_001L;
    private static final Long OTHER_BUYER = 910_002L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager em;

    private Long purchaseId;
    private Long otherPurchaseId;
    private Long productA;
    private Long productB;

    @BeforeEach
    void setUp() {
        long base = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
        purchaseId = base;
        otherPurchaseId = base + 1;
        productA = base + 10;
        productB = base + 11;
    }

    @Test
    @DisplayName("결제 대기 주문이 있으면 해당 상품의 주문이 존재한다")
    void existsWhenPaymentPending() {
        orderRepository.save(group(BUYER, purchaseId, lineA(2)));
        flushAndClear();

        assertThat(orderRepository.existsActiveOrderByIndividualGroupPurchaseAndProduct(purchaseId, productA)).isTrue();
    }

    @Test
    @DisplayName("결제 완료 주문이 있으면 해당 상품의 주문이 존재한다")
    void existsWhenPaid() {
        orderRepository.save(paid(group(BUYER, purchaseId, lineA(2))));
        flushAndClear();

        assertThat(orderRepository.existsActiveOrderByIndividualGroupPurchaseAndProduct(purchaseId, productA)).isTrue();
    }

    @Test
    @DisplayName("취소된 주문만 있으면 주문이 존재하지 않는다")
    void notExistsWhenCanceled() {
        Order canceled = group(BUYER, purchaseId, lineA(2));
        canceled.cancelByBuyer(BUYER, NOW);
        orderRepository.save(canceled);
        flushAndClear();

        assertThat(orderRepository.existsActiveOrderByIndividualGroupPurchaseAndProduct(purchaseId, productA)).isFalse();
    }

    @Test
    @DisplayName("다른 상품이나 다른 공동구매의 주문은 존재 여부에 포함되지 않는다")
    void notExistsForOtherProductOrPurchase() {
        orderRepository.save(group(BUYER, purchaseId, lineA(2)));
        orderRepository.save(group(BUYER, otherPurchaseId, lineB(1)));
        flushAndClear();

        assertThat(orderRepository.existsActiveOrderByIndividualGroupPurchaseAndProduct(purchaseId, productB)).isFalse();
        assertThat(orderRepository.existsActiveOrderByIndividualGroupPurchaseAndProduct(otherPurchaseId, productA)).isFalse();
    }

    @Test
    @DisplayName("개인 주문은 존재 여부에 포함되지 않는다")
    void notExistsForPersonalOrder() {
        orderRepository.save(Order.createPersonal(newOrderNumber(), BUYER, List.of(lineA(1)), address(), NOW));
        flushAndClear();

        assertThat(orderRepository.existsActiveOrderByIndividualGroupPurchaseAndProduct(purchaseId, productA)).isFalse();
    }

    private Order group(Long buyerMemberId, Long individualGroupPurchaseId, OrderLine... lines) {
        return Order.createIndividualGroup(newOrderNumber(), buyerMemberId, individualGroupPurchaseId,
                NOW.plusDays(1), List.of(lines), address(), NOW);
    }

    private Order paid(Order order) {
        order.markPaid(1L, NOW);
        return order;
    }

    private OrderLine lineA(int quantity) {
        return new OrderLine(productA, 1L, 7L, "상품A", "옵션A", 25000, quantity);
    }

    private OrderLine lineB(int quantity) {
        return new OrderLine(productB, 2L, 7L, "상품B", null, 15000, quantity);
    }

    private DeliveryAddress address() {
        return DeliveryAddress.of("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호");
    }

    private String newOrderNumber() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
