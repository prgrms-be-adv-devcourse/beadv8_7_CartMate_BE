package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.IndividualGroupPurchasePayback;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.IndividualGroupPurchaseReplica;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.OrderPurchaseConfirmation;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.IndividualGroupPurchasePaybackRepository;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.IndividualGroupPurchaseReplicaRepository;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.OrderPurchaseConfirmationRepository;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class IndividualGroupPurchasePaybackUseCaseTest {

    private static final Long PURCHASE = 7L;
    private static final LocalDateTime OPENED = LocalDateTime.of(2026, 11, 6, 12, 0);
    private static final LocalDateTime LATER = OPENED.plusDays(10);

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final IndividualGroupPurchaseReplicaRepository replicaRepository = mock(IndividualGroupPurchaseReplicaRepository.class);
    private final OrderPurchaseConfirmationRepository confirmationRepository = mock(OrderPurchaseConfirmationRepository.class);
    private final IndividualGroupPurchasePaybackRepository paybackRepository = mock(IndividualGroupPurchasePaybackRepository.class);
    private final PaybackPayoutUseCase payoutUseCase = mock(PaybackPayoutUseCase.class);
    private final List<OrderPurchaseConfirmation> confirmations = new ArrayList<>();

    private final IndividualGroupPurchasePaybackUseCase useCase = new IndividualGroupPurchasePaybackUseCase(
            orderRepository, replicaRepository, confirmationRepository, paybackRepository, payoutUseCase);

    @BeforeEach
    void setUp() {
        given(paybackRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(confirmationRepository.insertIfAbsent(any(), any())).willAnswer(invocation -> {
            Long orderId = invocation.getArgument(0);
            if (confirmations.stream().anyMatch(c -> c.getOrderId().equals(orderId))) {
                return 0;
            }
            confirmations.add(OrderPurchaseConfirmation.of(orderId, invocation.getArgument(1)));
            return 1;
        });
        given(confirmationRepository.findAllById(anyCollection())).willAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return confirmations.stream().filter(c -> ids.contains(c.getOrderId())).toList();
        });
    }

    @Test
    @DisplayName("끝난 어드민 공동구매의 마지막 주문이 구매확정되면 그때 한 번 판정한다")
    void judgeOnLastConfirmation() {
        givenReplica(closedWithPayback(2));
        givenOrders(order(1L, 10L, OrderStatus.PAID), order(2L, 20L, OrderStatus.PAID));

        useCase.recordConfirmed(1L, LATER, LATER);
        verify(paybackRepository, never()).save(any());

        useCase.recordConfirmed(2L, LATER, LATER);

        IndividualGroupPurchasePayback judged = savedJudgment();
        assertThat(judged.getIndividualGroupPurchaseId()).isEqualTo(PURCHASE);
        assertThat(judged.getBuyerCount()).isEqualTo(2);
        assertThat(judged.isReached()).isTrue();
        assertThat(judged.getPaybackAmount()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("달성하면 구매확정한 서로 다른 구매자에게 1명 1건 지급을 요청한다")
    void requestPayoutWhenReached() {
        givenReplica(closedWithPayback(2));
        givenOrders(order(1L, 10L, OrderStatus.PAID), order(2L, 20L, OrderStatus.PAID), order(3L, 10L, OrderStatus.PAID));

        useCase.recordConfirmed(1L, LATER, LATER);
        useCase.recordConfirmed(2L, LATER, LATER);
        useCase.recordConfirmed(3L, LATER, LATER);

        verify(payoutUseCase).requestAll(PURCHASE, Set.of(10L, 20L), 1_000L, LATER);
    }

    @Test
    @DisplayName("미달이면 판정만 남기고 지급을 요청하지 않는다")
    void noPayoutWhenNotReached() {
        givenReplica(closedWithPayback(3));
        givenOrders(order(1L, 10L, OrderStatus.PAID), order(2L, 20L, OrderStatus.PAID));

        useCase.recordConfirmed(1L, LATER, LATER);
        useCase.recordConfirmed(2L, LATER, LATER);

        assertThat(savedJudgment().isReached()).isFalse();
        verify(payoutUseCase, never()).requestAll(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("같은 구매확정이 두 번 와도 기록은 한 번이다")
    void idempotentConfirmation() {
        givenReplica(closedWithPayback(2));
        givenOrders(order(1L, 10L, OrderStatus.PAID), order(2L, 20L, OrderStatus.PAID));

        useCase.recordConfirmed(1L, LATER, LATER);
        useCase.recordConfirmed(1L, LATER.plusDays(1), LATER.plusDays(1));

        assertThat(confirmations).hasSize(1);
        assertThat(confirmations.getFirst().getConfirmedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("아직 판매 중이거나 페이백 조건이 없는(회원) 공동구매는 판정하지 않는다")
    void notJudged() {
        IndividualGroupPurchaseReplica open = IndividualGroupPurchaseReplica.of(PURCHASE, OPENED.plusDays(7), false, null, List.of(1L), 0);
        open.fillPaybackCondition(2, 1_000L);
        givenReplica(open);
        givenOrders(order(1L, 10L, OrderStatus.PAID));
        useCase.recordConfirmed(1L, OPENED.plusDays(3), OPENED.plusDays(3));

        givenReplica(IndividualGroupPurchaseReplica.of(PURCHASE, OPENED.plusDays(1), true, OPENED.plusDays(1), List.of(1L), 1));
        useCase.judge(PURCHASE, LATER);

        verify(paybackRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 판정했으면 다시 판정하지 않는다")
    void judgeOnce() {
        givenReplica(closedWithPayback(2));
        givenOrders();
        given(paybackRepository.existsById(PURCHASE)).willReturn(true);

        useCase.judge(PURCHASE, LATER);

        verify(paybackRepository, never()).save(any());
    }

    @Test
    @DisplayName("마지막 남은 주문이 취소되면 그 주문으로 판정을 일으키고, 취소한 구매자는 세지 않는다")
    void judgeOnLastCancel() {
        givenReplica(closedWithPayback(2));
        Order paid = order(1L, 10L, OrderStatus.PAID);
        Order canceled = order(2L, 20L, OrderStatus.CANCELED);
        givenOrders(paid, canceled);
        given(orderRepository.findById(2L)).willReturn(Optional.of(canceled));
        useCase.recordConfirmed(1L, LATER, LATER);
        verify(paybackRepository).save(any());

        IndividualGroupPurchasePayback judged = savedJudgment();
        assertThat(judged.getBuyerCount()).isEqualTo(1);
        assertThat(judged.isReached()).isFalse();
    }

    @Test
    @DisplayName("공동구매가 아닌 주문(개인 주문)의 구매확정도 기록만 하고 판정은 하지 않는다")
    void personalOrder() {
        Order personal = mock(Order.class);
        given(personal.getId()).willReturn(99L);
        given(personal.getIndividualGroupPurchaseId()).willReturn(null);
        given(orderRepository.findById(99L)).willReturn(Optional.of(personal));

        useCase.recordConfirmed(99L, LATER, LATER);

        assertThat(confirmations).extracting(OrderPurchaseConfirmation::getOrderId).containsExactly(99L);
        verify(replicaRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("주문이 없으면 판정도 기록도 하지 않는다")
    void unknownOrder() {
        given(orderRepository.findById(404L)).willReturn(Optional.empty());

        useCase.judgeOfOrder(404L, LATER);

        verify(replicaRepository, never()).findByIdForUpdate(any());
        verify(paybackRepository, never()).save(any());
    }

    private IndividualGroupPurchaseReplica closedWithPayback(int minBuyers) {
        IndividualGroupPurchaseReplica replica = IndividualGroupPurchaseReplica.of(
                PURCHASE, OPENED.plusDays(3), true, OPENED.plusDays(1), List.of(1L), 2);
        replica.fillPaybackCondition(minBuyers, 1_000L);
        return replica;
    }

    private void givenReplica(IndividualGroupPurchaseReplica replica) {
        given(replicaRepository.findByIdForUpdate(PURCHASE)).willReturn(Optional.of(replica));
    }

    private void givenOrders(Order... orders) {
        given(orderRepository.findAllByIndividualGroupPurchaseId(PURCHASE)).willReturn(List.of(orders));
        for (Order order : orders) {
            given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));
        }
    }

    private Order order(Long id, Long buyer, OrderStatus status) {
        Order order = mock(Order.class);
        given(order.getId()).willReturn(id);
        given(order.getBuyerMemberId()).willReturn(buyer);
        given(order.getStatus()).willReturn(status);
        given(order.getIndividualGroupPurchaseId()).willReturn(PURCHASE);
        return order;
    }

    private IndividualGroupPurchasePayback savedJudgment() {
        ArgumentCaptor<IndividualGroupPurchasePayback> captor = ArgumentCaptor.forClass(IndividualGroupPurchasePayback.class);
        verify(paybackRepository).save(captor.capture());
        return captor.getValue();
    }
}
