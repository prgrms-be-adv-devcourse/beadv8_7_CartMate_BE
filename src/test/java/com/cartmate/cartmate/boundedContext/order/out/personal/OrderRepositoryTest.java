package com.cartmate.cartmate.boundedContext.order.out.personal;

import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order.OrderLine;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OrderRepositoryTest {

    private static final Long BUYER = 900_001L;
    private static final Long OTHER_BUYER = 900_002L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("주문번호로 주문 존재 여부를 확인한다")
    void existsByOrderNumber() {
        String orderNumber = newOrderNumber();
        orderRepository.save(personal(orderNumber, BUYER));
        flushAndClear();

        assertThat(orderRepository.existsByOrderNumber(orderNumber)).isTrue();
        assertThat(orderRepository.existsByOrderNumber(newOrderNumber())).isFalse();
    }

    @Test
    @DisplayName("상세 조회는 주문 항목을 함께 불러온다")
    void findWithItemsById() {
        Order saved = orderRepository.save(personal(newOrderNumber(), BUYER));
        flushAndClear();

        Order found = orderRepository.findWithItemsById(saved.getId()).orElseThrow();
        flushAndClear();

        assertThat(found.getItems()).hasSize(2);
        assertThat(found.getTotalAmount()).isEqualTo(65000);
    }

    @Test
    @DisplayName("없는 주문이면 상세 조회 결과가 비어 있다")
    void findWithItemsByIdEmpty() {
        assertThat(orderRepository.findWithItemsById(999_999_999L)).isEmpty();
    }

    @Test
    @DisplayName("구매자의 주문만 최신순으로 조회한다")
    void findAllByMember() {
        Order first = orderRepository.save(personal(newOrderNumber(), BUYER));
        Order second = orderRepository.save(Order.createIndividualGroup(
                newOrderNumber(), BUYER, 500L, NOW.plusDays(1), lines(), address(), NOW));
        orderRepository.save(personal(newOrderNumber(), OTHER_BUYER));
        flushAndClear();

        List<Order> found = orderRepository.findAllByMember(BUYER);

        assertThat(found).extracting(Order::getId).containsExactly(second.getId(), first.getId());
        assertThat(found).allSatisfy(order -> assertThat(order.getItems()).hasSize(2));
    }

    @Test
    @DisplayName("결제 기한이 된 결제 대기 주문만 취소 대상이다")
    void findIdsToCancelByDeadline() {
        Order pending = orderRepository.save(personal(newOrderNumber(), BUYER));
        Order paid = personal(newOrderNumber(), BUYER);
        paid.markPaid(1L, NOW);
        orderRepository.save(paid);
        flushAndClear();
        LocalDateTime deadline = pending.getPaymentDeadlineAt();

        assertThat(orderRepository.findIdsToCancelByDeadline(deadline.minusSeconds(1))).doesNotContain(pending.getId());
        assertThat(orderRepository.findIdsToCancelByDeadline(deadline))
                .contains(pending.getId())
                .doesNotContain(paid.getId());
    }

    @Test
    @DisplayName("같은 주문번호로 주문을 두 번 만들 수 없다")
    void uniqueOrderNumber() {
        String orderNumber = newOrderNumber();
        orderRepository.save(personal(orderNumber, BUYER));
        flushAndClear();

        assertThatThrownBy(() -> {
            orderRepository.save(personal(orderNumber, OTHER_BUYER));
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Order personal(String orderNumber, Long buyerMemberId) {
        return Order.createPersonal(orderNumber, buyerMemberId, lines(), address(), NOW);
    }

    private List<OrderLine> lines() {
        return List.of(
                new OrderLine(10L, 1L, 7L, "상품A", "옵션A", 25000, 2),
                new OrderLine(11L, 2L, 7L, "상품B", null, 15000, 1));
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
