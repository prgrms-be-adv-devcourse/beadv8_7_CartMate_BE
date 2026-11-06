package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.global.http.InternalApiClient.InternalServerUnavailableException;

import com.cartmate.cartmate.shared.payment.dto.PaymentResult;

import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderPaymentClient;

import com.cartmate.cartmate.shared.order.OrderType;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.IndividualGroupPurchaseReplica;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.OrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.dto.DeliveryAddressInfo;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.addressWithoutDetail;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.ballLine;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.personalOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OrderPaymentUseCaseTest {

    private static final Long ORDER_ID = 100L;
    private static final Long BUYER = 1L;
    private static final Long PAYMENT_ID = 500L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 19, 12, 0);
    private static final Long PURCHASE_ID = 10L;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private IndividualGroupPurchaseReplicaUseCase replicaUseCase;

    private OrderPaymentUseCase paymentUseCase;

    @BeforeEach
    void setUp() {
        // 결제 반영 시 상품 재확인: 기본은 모든 상품이 판매 중
                OrderProductClient allOnSale = inventoryIds -> inventoryIds.stream().distinct()
                        .map(id -> new OrderProductInfo(id, id, 1L, "상품", null, 1000L, null, true))
                        .toList();
        // 결제 반영(applyPayment) 테스트라 결제 포트는 쓰지 않는다. 결제 흐름(pay)은 통합 테스트에서 확인
                paymentUseCase = new OrderPaymentUseCase(orderRepository, new OrderSupport(orderRepository), eventPublisher, allOnSale,
                        replicaUseCase, org.mockito.Mockito.mock(com.cartmate.cartmate.boundedContext.orderService.order.out.OrderPaymentClient.class));
    }

    @Test
    @DisplayName("결제 기한 안에 결제하면 결제 완료로 바꾸고 결제 완료 이벤트를 한 번 발행한다")
    void applyPayment() {
        Order order = personalOrder(BUYER, ballLine(9L, 2), CREATED_AT);
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        LocalDateTime paidAt = CREATED_AT.plusMinutes(10);

        paymentUseCase.applyPayment(ORDER_ID, BUYER, PAYMENT_ID, paidAt);

        ArgumentCaptor<OrderPaidEvent> captor = ArgumentCaptor.forClass(OrderPaidEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        OrderPaidEvent event = captor.getValue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(event.memberId()).isEqualTo(BUYER);
        assertThat(event.orderType()).isEqualTo(OrderType.PERSONAL);
        assertThat(event.paidAt()).isEqualTo(paidAt);
        assertThat(event.orderNumber()).isEqualTo("20261019-ABCDEFGH");
        assertThat(event.deliveryAddress())
                .isEqualTo(new DeliveryAddressInfo("홍길동", "010-1234-5678", "12345", "서울시 강남구", null));
        assertThat(event.items())
                .extracting(OrderPaidEvent.Item::productId, OrderPaidEvent.Item::inventoryId,
                        OrderPaidEvent.Item::sellerId, OrderPaidEvent.Item::productName, OrderPaidEvent.Item::optionName,
                        OrderPaidEvent.Item::quantity, OrderPaidEvent.Item::unitPrice)
                .containsExactly(tuple(2L, 4L, 9L, "축구공", "5호", 2, 25000L));
    }

    @Test
    @DisplayName("결제 기한이 지난 결제는 반영하지 않고 이벤트도 발행하지 않는다")
    void applyPaymentAfterDeadline() {
        Order order = personalOrder(BUYER, ballLine(9L, 2), CREATED_AT);
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentUseCase.applyPayment(ORDER_ID, BUYER, PAYMENT_ID,
                CREATED_AT.plus(Order.PAYMENT_PERIOD).plusSeconds(1)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_PAYMENT_EXPIRED.getMsg());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        then(eventPublisher).should(never()).publish(any(DomainEvent.class));
    }

    @Test
    @DisplayName("같은 결제 ID로 결제 통보가 두 번 와도 예외 없이 결제 완료 이벤트는 한 번만 발행한다")
    void applyPaymentSamePaymentIdTwice() {
        Order order = personalOrder(BUYER, ballLine(9L, 2), CREATED_AT);
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        LocalDateTime paidAt = CREATED_AT.plusMinutes(10);

        paymentUseCase.applyPayment(ORDER_ID, BUYER, PAYMENT_ID, paidAt);
        paymentUseCase.applyPayment(ORDER_ID, BUYER, PAYMENT_ID, paidAt);

        then(eventPublisher).should(times(1)).publish(any(OrderPaidEvent.class));
        assertThat(order.getPaymentId()).isEqualTo(PAYMENT_ID);
    }

    @Test
    @DisplayName("이미 다른 결제 ID로 결제된 주문은 다시 결제할 수 없다")
    void applyPaymentDifferentPaymentId() {
        Order order = personalOrder(BUYER, ballLine(9L, 2), CREATED_AT);
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        LocalDateTime paidAt = CREATED_AT.plusMinutes(10);
        paymentUseCase.applyPayment(ORDER_ID, BUYER, PAYMENT_ID, paidAt);

        assertThatThrownBy(() -> paymentUseCase.applyPayment(ORDER_ID, BUYER, PAYMENT_ID + 1, paidAt))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NOT_PAYMENT_PENDING.getMsg());
        then(eventPublisher).should(times(1)).publish(any(OrderPaidEvent.class));
    }

    @Test
    @DisplayName("열려 있는 공동구매의 개인물품 주문은 결제할 수 있다")
    void payIndividualGroupWhileOpen() {
        Order order = individualGroupOrder(CREATED_AT);
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(replicaUseCase.findForPayment(PURCHASE_ID)).willReturn(Optional.of(IndividualGroupPurchaseReplica.of(
                PURCHASE_ID, CREATED_AT.plusDays(1), false, null, List.of(2L), 1)));

        paymentUseCase.applyPayment(ORDER_ID, BUYER, PAYMENT_ID, CREATED_AT.plusMinutes(5));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("공동구매가 닫히면 닫히기 전에 만든 주문도 결제 정보에서 결제 불가이고 결제 반영도 거절한다 (결제 마감 = 판매 마감)")
    void rejectIndividualGroupAfterClose() {
        Order order = individualGroupOrder(CREATED_AT);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        IndividualGroupPurchaseReplica closed = IndividualGroupPurchaseReplica.of(
                PURCHASE_ID, CREATED_AT.plusDays(1), true, CREATED_AT.plusMinutes(1), List.of(2L), 3);
        given(replicaUseCase.find(PURCHASE_ID)).willReturn(Optional.of(closed));
        given(replicaUseCase.findForPayment(PURCHASE_ID)).willReturn(Optional.of(closed));

        assertThat(paymentUseCase.getPaymentInfo(ORDER_ID, BUYER, CREATED_AT.plusMinutes(2)).payable()).isFalse();
        assertThatThrownBy(() -> paymentUseCase.applyPayment(ORDER_ID, BUYER, PAYMENT_ID, CREATED_AT.plusMinutes(2)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_GROUP_PURCHASE_PAYMENT_CLOSED.getMsg());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    @DisplayName("주문 상품이 공동구매에서 빠졌으면(제거와 주문이 겹침) 결제를 거절하고, 공동구매를 못 찾아도 거절한다")
    void rejectIndividualGroupProductRemovedOrUnknown() {
        Order order = individualGroupOrder(CREATED_AT);
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(replicaUseCase.findForPayment(PURCHASE_ID))
                .willReturn(Optional.of(IndividualGroupPurchaseReplica.of(
                        PURCHASE_ID, CREATED_AT.plusDays(1), false, null, List.of(3L), 2)))
                .willReturn(Optional.empty());

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> paymentUseCase.applyPayment(ORDER_ID, BUYER, PAYMENT_ID, CREATED_AT.plusMinutes(1)))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining(OrderErrorCode.ORDER_GROUP_PURCHASE_PAYMENT_CLOSED.getMsg());
        }
    }

    @Test
    @DisplayName("차감 뒤 반영 중 상품 서버가 잠깐 503이면 차감을 되돌리지 않고 그대로 던진다 — 다시 결제하면 반영만 한다 (ISS-27)")
    void doNotCompensateOnTemporaryOutage() {
        OrderPaymentClient paymentClient = org.mockito.Mockito.mock(OrderPaymentClient.class);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        OrderProductClient flaky = inventoryIds -> {
            if (calls.incrementAndGet() > 1) {  // 차감 전 확인은 통과, 반영 단계 재확인에서 503
                throw new InternalServerUnavailableException("PRODUCT", "상품", null);
            }
            return inventoryIds.stream().map(id -> new OrderProductInfo(id, id, 1L, "상품", null, 1000L, null, true)).toList();
        };
        OrderPaymentUseCase useCase = new OrderPaymentUseCase(orderRepository, new OrderSupport(orderRepository),
                eventPublisher, flaky, replicaUseCase, paymentClient);
        Order order = personalOrder(BUYER, ballLine(9L, 2), CREATED_AT);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentClient.findPayment(any(), any(), any())).willReturn(Optional.empty());
        given(paymentClient.charge(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong())).willReturn(new PaymentResult(
                PAYMENT_ID, ORDER_ID, 50000L, 0L, 50000L, "INDIVIDUAL_GROUP", "PAID", CREATED_AT.plusMinutes(1), null));

        assertThatThrownBy(() -> useCase.pay(ORDER_ID, BUYER, CREATED_AT.plusMinutes(1)))
                .isInstanceOf(InternalServerUnavailableException.class);
        then(paymentClient).should(never()).compensateCharge(any(), any(), any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    @DisplayName("이미 취소된 주문에 늦게 온 결제 반영은 상품 서버를 부르지 않고 업무 거절로 끝나 차감을 되돌린다 (취소 · 결제 경합)")
    void canceledOrderCompensatesWithoutCallingProductServer() {
        OrderPaymentClient paymentClient = org.mockito.Mockito.mock(OrderPaymentClient.class);
        OrderProductClient mustNotBeCalled = inventoryIds -> {
            throw new InternalServerUnavailableException("PRODUCT", "상품", null);
        };
        OrderPaymentUseCase useCase = new OrderPaymentUseCase(orderRepository, new OrderSupport(orderRepository),
                eventPublisher, mustNotBeCalled, replicaUseCase, paymentClient);
        Order order = personalOrder(BUYER, ballLine(9L, 2), CREATED_AT);
        order.cancelByBuyer(BUYER, CREATED_AT.plusMinutes(1));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        PaymentResult charged = new PaymentResult(PAYMENT_ID, ORDER_ID, 50000L, 0L, 50000L, "INDIVIDUAL_GROUP", "PAID",
                CREATED_AT.plusMinutes(2), null);
        given(paymentClient.findPayment(any(), any(), any())).willReturn(Optional.of(charged));

        assertThatThrownBy(() -> useCase.pay(ORDER_ID, BUYER, CREATED_AT.plusMinutes(2)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NOT_PAYMENT_PENDING.getMsg());
        then(paymentClient).should().compensateCharge(any(), any(), any());
    }

    private Order individualGroupOrder(LocalDateTime createdAt) {
        Order order = Order.createIndividualGroup("20261019-IGPORDER", BUYER, PURCHASE_ID, createdAt.plusDays(1),
                List.of(ballLine(9L, 2)), addressWithoutDetail(), createdAt);
        ReflectionTestUtils.setField(order, "createdAt", createdAt); // @CreationTimestamp는 저장할 때 채워진다
        return order;
    }
}
