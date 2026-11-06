package com.cartmate.cartmate.boundedContext.orderService.order.out.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order.OrderLine;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.OrderItem;
import com.cartmate.cartmate.support.CartMateJpaTest;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.individualGroupOrder;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.personalOrder;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.productALine;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.productBLine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@CartMateJpaTest
class OrderRepositoryTest {

    private static final Long BUYER = 900_001L;
    private static final Long OTHER_BUYER = 900_002L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager em;

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
    @DisplayName("구매자의 주문만 최신순으로 조회한다")
    void findAllByMember() {
        Order first = orderRepository.save(personal(newOrderNumber(), BUYER));
        Order second = orderRepository.save(individualGroupOrder(newOrderNumber(), BUYER, 500L, lines(), NOW));
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

    @Test
    @DisplayName("같은 회원이 같은 재고 상품의 결제 대기 개인 주문을 둘 만들면 DB가 거절한다 (조회와 저장 사이 경쟁 대비)")
    void uniquePendingPersonalItem() {
        Long buyer = uniqueMemberId();
        orderRepository.save(personal(newOrderNumber(), buyer));
        flushAndClear();

        DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> orderRepository.saveAndFlush(personalOrder(newOrderNumber(), buyer,
                        List.of(productBLine(11L, 1)), NOW)));

        // OrderCreateUseCase가 판별에 쓰는 두 경로(Hibernate 제약 이름, 가장 안쪽 DB 메시지)를 실제 MySQL에서 확인한다
        assertThat(e).isNotNull();
        assertThat(e.getCause()).isInstanceOf(ConstraintViolationException.class);
        assertThat(((ConstraintViolationException) e.getCause()).getConstraintName())
                .contains(OrderItem.PENDING_BUYER_INVENTORY_UNIQUE);
        assertThat(e.getMostSpecificCause().getMessage()).contains(OrderItem.PENDING_BUYER_INVENTORY_UNIQUE);
    }

    @Test
    @DisplayName("앞 주문이 결제 · 취소되면 같은 상품으로 다시 결제 대기 주문을 만들 수 있고, 다른 회원 · 개인물품 공동구매 주문은 겹쳐도 된다")
    void pendingUniqueReleasedAfterPaidOrCanceled() {
        Long buyer = uniqueMemberId();
        Order paid = orderRepository.save(personal(newOrderNumber(), buyer));
        flushAndClear();
        Order loadedPaid = orderRepository.findWithItemsById(paid.getId()).orElseThrow();
        loadedPaid.markPaid(1L, NOW);
        flushAndClear();

        Order canceled = orderRepository.saveAndFlush(personal(newOrderNumber(), buyer));
        Order loadedCanceled = orderRepository.findWithItemsById(canceled.getId()).orElseThrow();
        loadedCanceled.cancelByBuyer(buyer, NOW);
        flushAndClear();

        orderRepository.saveAndFlush(personal(newOrderNumber(), buyer));
        orderRepository.saveAndFlush(personal(newOrderNumber(), buyer + 1));
        orderRepository.saveAndFlush(individualGroup(buyer, uniqueMemberId()));
        orderRepository.saveAndFlush(individualGroup(buyer, uniqueMemberId()));
        flushAndClear();

        assertThat(orderRepository.findWithItemsById(loadedPaid.getId()).orElseThrow().getItems())
                .allSatisfy(item -> assertThat(item.getPendingBuyerMemberId()).isNull());
        assertThat(orderRepository.findWithItemsById(loadedCanceled.getId()).orElseThrow().getItems())
                .allSatisfy(item -> assertThat(item.getPendingBuyerMemberId()).isNull());
    }

    @Test
    @DisplayName("같은 회원이 같은 공동구매에 결제 대기 주문을 둘 만들면 DB가 거절한다 (공동구매 행 락 대신)")
    void uniquePendingIndividualGroupOrder() {
        Long buyer = uniqueMemberId();
        Long purchaseId = uniqueMemberId();
        Order first = orderRepository.saveAndFlush(individualGroup(buyer, purchaseId));
        assertThat(first.getPendingIndividualGroupPurchaseId()).isEqualTo(purchaseId);
        flushAndClear();

        DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> orderRepository.saveAndFlush(individualGroup(buyer, purchaseId)));

        // OrderCreateUseCase가 판별에 쓰는 두 경로(Hibernate 제약 이름, 가장 안쪽 DB 메시지)를 실제 MySQL에서 확인한다
        assertThat(e).isNotNull();
        assertThat(((ConstraintViolationException) e.getCause()).getConstraintName())
                .contains(Order.PENDING_BUYER_INDIVIDUAL_GROUP_UNIQUE);
        assertThat(e.getMostSpecificCause().getMessage()).contains(Order.PENDING_BUYER_INDIVIDUAL_GROUP_UNIQUE);
    }

    @Test
    @DisplayName("결제 · 취소된 개인물품 주문은 unique 대상에서 빠지고, 다른 회원 · 다른 공동구매 · 개인 주문은 겹쳐도 된다")
    void pendingIndividualGroupUniqueReleased() {
        Long buyer = uniqueMemberId();
        Long purchaseId = uniqueMemberId();
        Order paid = orderRepository.saveAndFlush(individualGroup(buyer, purchaseId));
        flushAndClear();
        Order loadedPaid = orderRepository.findWithItemsById(paid.getId()).orElseThrow();
        loadedPaid.markPaid(1L, NOW);
        flushAndClear();
        Order canceled = orderRepository.saveAndFlush(individualGroup(buyer, purchaseId));
        Order loadedCanceled = orderRepository.findWithItemsById(canceled.getId()).orElseThrow();
        loadedCanceled.cancelByBuyer(buyer, NOW);
        flushAndClear();

        orderRepository.saveAndFlush(individualGroup(buyer, purchaseId));
        orderRepository.saveAndFlush(individualGroup(buyer + 1, purchaseId));
        orderRepository.saveAndFlush(individualGroup(buyer, purchaseId + 1));
        Order personal = orderRepository.saveAndFlush(personal(newOrderNumber(), buyer));
        flushAndClear();

        assertThat(orderRepository.findById(loadedPaid.getId()).orElseThrow().getPendingIndividualGroupPurchaseId()).isNull();
        assertThat(orderRepository.findById(loadedCanceled.getId()).orElseThrow().getPendingIndividualGroupPurchaseId()).isNull();
        assertThat(orderRepository.findById(personal.getId()).orElseThrow().getPendingIndividualGroupPurchaseId()).isNull();
    }

    @Test
    @DisplayName("같은 공동구매의 결제 대기 주문만 찾고 결제 완료·취소 주문은 찾지 않는다")
    void findPendingIndividualGroupOrderId() {
        Long buyer = uniqueMemberId();
        Long purchaseId = uniqueMemberId();
        Order paid = individualGroup(buyer, purchaseId);
        paid.markPaid(1L, NOW);
        orderRepository.save(paid);
        Order canceled = individualGroup(buyer, purchaseId);
        canceled.cancelByBuyer(buyer, NOW);
        orderRepository.save(canceled);
        orderRepository.save(individualGroup(buyer, purchaseId + 1));
        flushAndClear();

        assertThat(orderRepository.findPendingIndividualGroupOrderId(buyer, purchaseId)).isEmpty();

        Order pending = orderRepository.save(individualGroup(buyer, purchaseId));
        flushAndClear();

        assertThat(orderRepository.findPendingIndividualGroupOrderId(buyer, purchaseId)).contains(pending.getId());
        assertThat(orderRepository.findPendingIndividualGroupOrderId(buyer + 1, purchaseId)).isEmpty();
    }

    @Test
    @DisplayName("겹치는 상품이 있는 개인 결제 대기 주문만 찾고 결제 완료·취소 주문은 찾지 않는다")
    void findPendingPersonalOrderIdContaining() {
        Long buyer = uniqueMemberId();
        Order paid = personal(newOrderNumber(), buyer);
        paid.markPaid(1L, NOW);
        orderRepository.save(paid);
        Order canceled = personal(newOrderNumber(), buyer);
        canceled.cancelByBuyer(buyer, NOW);
        orderRepository.save(canceled);
        flushAndClear();

        assertThat(orderRepository.findPendingPersonalOrderIdContaining(buyer, List.of(1L, 2L))).isEmpty();

        Order pending = orderRepository.save(personal(newOrderNumber(), buyer));
        flushAndClear();

        assertThat(orderRepository.findPendingPersonalOrderIdContaining(buyer, List.of(2L, 99L))).contains(pending.getId());
        assertThat(orderRepository.findPendingPersonalOrderIdContaining(buyer, List.of(99L))).isEmpty();
        assertThat(orderRepository.findPendingPersonalOrderIdContaining(buyer + 1, List.of(1L))).isEmpty();
    }

    private Order personal(String orderNumber, Long buyerMemberId) {
        return personalOrder(orderNumber, buyerMemberId, lines(), NOW);
    }

    private Order individualGroup(Long buyerMemberId, Long purchaseId) {
        return individualGroupOrder(newOrderNumber(), buyerMemberId, purchaseId, lines(), NOW);
    }

    private Long uniqueMemberId() {
        return 9_700_000_000L + ThreadLocalRandom.current().nextLong(100_000_000L) * 10;
    }

    private List<OrderLine> lines() {
        return List.of(productALine(10L, 2), productBLine(11L, 1));
    }

    private String newOrderNumber() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
