package com.cartmate.cartmate.boundedContext.orderService.order.out.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order.OrderLine;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.support.CartMateJpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest(name = "{0} 주문만 있으면 존재 여부 {1}", quoteTextArguments = false)
    @CsvSource({"PAYMENT_PENDING, true", "PAID, true", "CANCELED, false"})
    @DisplayName("결제 대기 · 결제 완료 주문은 해당 상품의 주문으로 세고, 취소된 주문은 세지 않는다")
    void existsByStatus(OrderStatus status, boolean expected) {
        Order order = group(BUYER, purchaseId, lineA(2));
        if (status == OrderStatus.PAID) {
            order.markPaid(1L, NOW);
        } else if (status == OrderStatus.CANCELED) {
            order.cancelByBuyer(BUYER, NOW);
        }
        orderRepository.save(order);
        flushAndClear();

        assertThat(orderRepository.existsActiveOrderByIndividualGroupPurchaseAndProduct(purchaseId, productA)).isEqualTo(expected);
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
