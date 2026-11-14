package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.global.http.InternalApiClient.InternalServerUnavailableException;

import com.cartmate.cartmate.shared.payment.dto.PaymentResult;

import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderInventoryClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderPaymentClient;
import com.cartmate.cartmate.shared.product.dto.InventoryReserveRequest;

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
import com.cartmate.cartmate.shared.coupon.dto.CouponQuote;
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

    @Mock
    private OrderInventoryClient inventoryClient;

    private OrderPaymentUseCase paymentUseCase;

    @BeforeEach
    void setUp() {
                OrderProductClient allOnSale = inventoryIds -> inventoryIds.stream().distinct()
                        .map(id -> new OrderProductInfo(id, id, 1L, "상품", null, 1000L, null, true))
                        .toList();
                paymentUseCase = new OrderPaymentUseCase(orderRepository, new OrderSupport(orderRepository), eventPublisher, allOnSale,
                        replicaUseCase, org.mockito.Mockito.mock(com.cartmate.cartmate.boundedContext.orderService.order.out.OrderPaymentClient.class),
                        org.mockito.Mockito.mock(com.cartmate.cartmate.boundedContext.orderService.order.out.OrderCouponClient.class), inventoryClient);
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
            if (calls.incrementAndGet() > 1) {
                throw new InternalServerUnavailableException("PRODUCT", "상품", null);
            }
            return inventoryIds.stream().map(id -> new OrderProductInfo(id, id, 1L, "상품", null, 1000L, null, true)).toList();
        };
        OrderPaymentUseCase useCase = new OrderPaymentUseCase(orderRepository, new OrderSupport(orderRepository),
                eventPublisher, flaky, replicaUseCase, paymentClient,
                org.mockito.Mockito.mock(com.cartmate.cartmate.boundedContext.orderService.order.out.OrderCouponClient.class), inventoryClient);
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
                eventPublisher, mustNotBeCalled, replicaUseCase, paymentClient,
                org.mockito.Mockito.mock(com.cartmate.cartmate.boundedContext.orderService.order.out.OrderCouponClient.class), inventoryClient);
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

    @Test
    @DisplayName("취소 처리 중인 주문에 같은 결제로 결제가 다시 오면 반영된 것으로 보고 되돌리지 않으며 상태 · 이벤트도 그대로다 (ISS-36)")
    void cancelRequestedOrderKeepsPaymentOnRetry() {
        OrderPaymentClient paymentClient = org.mockito.Mockito.mock(OrderPaymentClient.class);
        OrderPaymentUseCase useCase = new OrderPaymentUseCase(orderRepository, new OrderSupport(orderRepository),
                eventPublisher, inventoryIds -> List.of(), replicaUseCase, paymentClient,
                org.mockito.Mockito.mock(com.cartmate.cartmate.boundedContext.orderService.order.out.OrderCouponClient.class), inventoryClient);
        Order order = personalOrder(BUYER, ballLine(9L, 2), CREATED_AT);
        order.markPaid(PAYMENT_ID, CREATED_AT.plusMinutes(1));
        order.cancelByBuyer(BUYER, CREATED_AT.plusMinutes(2));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentClient.findPayment(any(), any(), any())).willReturn(Optional.of(new PaymentResult(PAYMENT_ID, ORDER_ID,
                50000L, 0L, 50000L, "INDIVIDUAL_GROUP", "PAID", CREATED_AT.plusMinutes(1), null)));

        useCase.pay(ORDER_ID, BUYER, CREATED_AT.plusMinutes(3));

        then(paymentClient).should(never()).compensateCharge(any(), any(), any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("차감에 응답이 없고 재조회로도 결제를 확인하지 못하면 주문을 결제 대기로 두고 되돌리기 · 이벤트 없이 503을 그대로 던진다")
    void chargeUnavailableKeepsOrderPending() {
        assertChargeFailureLeavesOrderPending(new InternalServerUnavailableException("PAYMENT", "결제", null));
    }

    @Test
    @DisplayName("결제 서버가 차감을 업무 거절하면 주문을 결제 대기로 두고 되돌리기 · 이벤트 없이 그 예외를 그대로 던진다")
    void chargeRejectedKeepsOrderPending() {
        assertChargeFailureLeavesOrderPending(new ServiceException("400-INSUFFICIENT_BALANCE", "예치금이 부족합니다."));
    }

    @Test
    @DisplayName("재고 선점: 결제 직전에 주문의 재고 · 수량으로 선점하고, 그 뒤에 차감한다")
    void payReservesStockBeforeCharge() {
        OrderPaymentClient paymentClient = org.mockito.Mockito.mock(OrderPaymentClient.class);
        OrderPaymentUseCase useCase = payingUseCase(paymentClient);
        Order order = personalOrder(BUYER, ballLine(9L, 2), CREATED_AT);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentClient.findPayment(any(), any(), any())).willReturn(Optional.empty());
        given(paymentClient.charge(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong())).willReturn(new PaymentResult(
                PAYMENT_ID, ORDER_ID, 50000L, 0L, 50000L, "INDIVIDUAL_GROUP", "PAID", CREATED_AT.plusMinutes(1), null));

        useCase.pay(ORDER_ID, BUYER, CREATED_AT.plusMinutes(1));

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(inventoryClient, paymentClient);
        inOrder.verify(inventoryClient).reserve(OrderType.PERSONAL, ORDER_ID, List.of(new InventoryReserveRequest.Item(4L, 2)));
        inOrder.verify(paymentClient).charge(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
        then(inventoryClient).should(never()).release(any(), any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("재고 선점: 남은 수량이 부족하면 ORDER_OUT_OF_STOCK이고 차감하지 않는다")
    void payOutOfStockDoesNotCharge() {
        OrderPaymentClient paymentClient = org.mockito.Mockito.mock(OrderPaymentClient.class);
        OrderPaymentUseCase useCase = payingUseCase(paymentClient);
        Order order = personalOrder(BUYER, ballLine(9L, 2), CREATED_AT);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentClient.findPayment(any(), any(), any())).willReturn(Optional.empty());
        org.mockito.BDDMockito.willThrow(new ServiceException(OrderErrorCode.ORDER_OUT_OF_STOCK))
                .given(inventoryClient).reserve(any(), any(), any());

        assertThatThrownBy(() -> useCase.pay(ORDER_ID, BUYER, CREATED_AT.plusMinutes(1)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_OUT_OF_STOCK.getMsg());
        then(paymentClient).should(never()).charge(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    @DisplayName("선점 해제: 결제 서버가 차감을 거절하면(결제 실패 확실) 선점을 해제한다")
    void chargeRejectedReleasesStock() {
        assertChargeFailureLeavesOrderPending(new ServiceException("400-INSUFFICIENT_BALANCE", "예치금이 부족합니다."));

        then(inventoryClient).should().release(OrderType.PERSONAL, ORDER_ID);
    }

    @Test
    @DisplayName("선점 해제 안 함: 차감에 응답이 없으면(결제됐는지 모름) 선점을 그대로 둔다")
    void chargeUnavailableKeepsReservation() {
        assertChargeFailureLeavesOrderPending(new InternalServerUnavailableException("PAYMENT", "결제", null));

        then(inventoryClient).should(never()).release(any(), any());
    }

    @Test
    @DisplayName("선점 해제: 차감 뒤 반영이 업무 거절돼 차감을 되돌리면 선점도 해제한다")
    void compensatedPaymentReleasesStock() {
        OrderPaymentClient paymentClient = org.mockito.Mockito.mock(OrderPaymentClient.class);
        OrderPaymentUseCase useCase = payingUseCase(paymentClient);
        Order order = personalOrder(BUYER, ballLine(9L, 2), CREATED_AT);
        order.cancelByBuyer(BUYER, CREATED_AT.plusMinutes(1));
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentClient.findPayment(any(), any(), any())).willReturn(Optional.of(new PaymentResult(PAYMENT_ID, ORDER_ID,
                50000L, 0L, 50000L, "INDIVIDUAL_GROUP", "PAID", CREATED_AT.plusMinutes(2), null)));

        assertThatThrownBy(() -> useCase.pay(ORDER_ID, BUYER, CREATED_AT.plusMinutes(2)))
                .isInstanceOf(ServiceException.class);
        then(paymentClient).should().compensateCharge(any(), any(), any());
        then(inventoryClient).should().release(OrderType.PERSONAL, ORDER_ID);
    }

    private OrderPaymentUseCase payingUseCase(OrderPaymentClient paymentClient) {
        OrderProductClient allOnSale = inventoryIds -> inventoryIds.stream().distinct()
                .map(id -> new OrderProductInfo(id, id, 1L, "상품", null, 1000L, null, true))
                .toList();
        return new OrderPaymentUseCase(orderRepository, new OrderSupport(orderRepository), eventPublisher, allOnSale,
                replicaUseCase, paymentClient,
                org.mockito.Mockito.mock(com.cartmate.cartmate.boundedContext.orderService.order.out.OrderCouponClient.class),
                inventoryClient);
    }

    private void assertChargeFailureLeavesOrderPending(ServiceException failure) {
        OrderPaymentClient paymentClient = org.mockito.Mockito.mock(OrderPaymentClient.class);
        OrderProductClient allOnSale = inventoryIds -> inventoryIds.stream().distinct()
                .map(id -> new OrderProductInfo(id, id, 1L, "상품", null, 1000L, null, true))
                .toList();
        OrderPaymentUseCase useCase = new OrderPaymentUseCase(orderRepository, new OrderSupport(orderRepository),
                eventPublisher, allOnSale, replicaUseCase, paymentClient,
                org.mockito.Mockito.mock(com.cartmate.cartmate.boundedContext.orderService.order.out.OrderCouponClient.class), inventoryClient);
        Order order = personalOrder(BUYER, ballLine(9L, 2), CREATED_AT);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentClient.findPayment(any(), any(), any())).willReturn(Optional.empty());
        given(paymentClient.charge(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong())).willThrow(failure);

        assertThatThrownBy(() -> useCase.pay(ORDER_ID, BUYER, CREATED_AT.plusMinutes(1))).isSameAs(failure);
        then(paymentClient).should(never()).compensateCharge(any(), any(), any());
        then(orderRepository).should(never()).findByIdForUpdate(any());
        then(eventPublisher).should(never()).publish(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    @DisplayName("쿠폰 결제는 상품 서버가 계산한 할인만큼 덜 차감하고, 사용 결과 할인으로 주문을 결제 완료해 결제액이 차감액과 같다")
    void payWithCouponChargesDiscountedAmount() {
        CouponFixture f = couponFixture();
        given(f.couponClient.quote(any(), any(), any(), any())).willReturn(QUOTE);
        given(f.couponClient.use(any(), any(), any(), any())).willReturn(QUOTE);
        given(f.paymentClient.charge(any(), any(), any(), org.mockito.ArgumentMatchers.eq(45_000L))).willReturn(charged(45_000L));

        f.useCase.pay(ORDER_ID, BUYER, 5L, CREATED_AT.plusMinutes(1));

        assertThat(f.order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(f.order.getMemberCouponId()).isEqualTo(5L);
        assertThat(f.order.payAmount()).isEqualTo(45_000L);
        then(f.couponClient).should().use(org.mockito.ArgumentMatchers.eq(BUYER), org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(ORDER_ID), any());
        then(f.couponClient).should(never()).restore(any(), any(), any());
    }

    @Test
    @DisplayName("결제 반영 중 쿠폰 사용이 거절되면 차감을 되돌리고 그 주문 기준으로 쿠폰 복구도 요청한 뒤 쿠폰 오류를 그대로 던진다")
    void couponRejectedCompensatesChargeAndRestoresCoupon() {
        CouponFixture f = couponFixture();
        ServiceException expired = new ServiceException("409-COUPON_EXPIRED", "사용 기간이 지난 쿠폰입니다.");
        given(f.couponClient.quote(any(), any(), any(), any())).willReturn(QUOTE);
        given(f.couponClient.use(any(), any(), any(), any())).willThrow(expired);
        given(f.paymentClient.charge(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong())).willReturn(charged(45_000L));

        assertThatThrownBy(() -> f.useCase.pay(ORDER_ID, BUYER, 5L, CREATED_AT.plusMinutes(1))).isSameAs(expired);
        then(f.paymentClient).should().compensateCharge(any(), any(), any());
        then(f.couponClient).should().restore(BUYER, 5L, ORDER_ID);
    }

    @Test
    @DisplayName("쿠폰을 쓴 뒤 결제액이 맞지 않아 반영이 실패하면 차감을 되돌리고 쿠폰도 복구 요청한다")
    void amountMismatchAfterCouponUseRestoresCoupon() {
        CouponFixture f = couponFixture();
        given(f.couponClient.quote(any(), any(), any(), any())).willReturn(QUOTE);
        given(f.couponClient.use(any(), any(), any(), any())).willReturn(QUOTE);
        given(f.paymentClient.charge(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong())).willReturn(charged(50_000L));

        assertThatThrownBy(() -> f.useCase.pay(ORDER_ID, BUYER, 5L, CREATED_AT.plusMinutes(1)))
                .hasMessageContaining(OrderErrorCode.ORDER_PAYMENT_AMOUNT_MISMATCH.getMsg());
        then(f.paymentClient).should().compensateCharge(any(), any(), any());
        then(f.couponClient).should().restore(BUYER, 5L, ORDER_ID);
    }

    @Test
    @DisplayName("쿠폰 사용에 상품 서버가 응답하지 않으면 차감은 다시 결제할 때 반영하도록 두고, 쿠폰은 복구 요청해 주문과 맞춘 뒤 503을 던진다")
    void couponUnavailableKeepsChargeAndRestoresCoupon() {
        CouponFixture f = couponFixture();
        InternalServerUnavailableException timeout = new InternalServerUnavailableException("PRODUCT", "상품", null);
        given(f.couponClient.quote(any(), any(), any(), any())).willReturn(QUOTE);
        given(f.couponClient.use(any(), any(), any(), any())).willThrow(timeout);
        given(f.paymentClient.charge(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong())).willReturn(charged(45_000L));

        assertThatThrownBy(() -> f.useCase.pay(ORDER_ID, BUYER, 5L, CREATED_AT.plusMinutes(1))).isSameAs(timeout);
        then(f.paymentClient).should(never()).compensateCharge(any(), any(), any());
        then(f.couponClient).should().restore(BUYER, 5L, ORDER_ID);
        assertThat(f.order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    @DisplayName("보상 중 쿠폰 복구까지 실패해도 원래 결제 반영 오류를 그대로 던진다")
    void restoreFailureKeepsOriginalError() {
        CouponFixture f = couponFixture();
        ServiceException expired = new ServiceException("409-COUPON_EXPIRED", "사용 기간이 지난 쿠폰입니다.");
        given(f.couponClient.quote(any(), any(), any(), any())).willReturn(QUOTE);
        given(f.couponClient.use(any(), any(), any(), any())).willThrow(expired);
        org.mockito.BDDMockito.willThrow(new InternalServerUnavailableException("PRODUCT", "상품", null))
                .given(f.couponClient).restore(any(), any(), any());
        given(f.paymentClient.charge(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong())).willReturn(charged(45_000L));

        assertThatThrownBy(() -> f.useCase.pay(ORDER_ID, BUYER, 5L, CREATED_AT.plusMinutes(1))).isSameAs(expired);
    }

    @Test
    @DisplayName("이미 쿠폰으로 결제 완료된 주문은 반영이 실패해도 그 주문에 쓴 쿠폰을 복구하지 않는다")
    void paidOrderFailureDoesNotRestoreCoupon() {
        CouponFixture f = couponFixture();
        f.order.applyCoupon(5L, 5_000L, true, null);
        f.order.markPaid(999L, CREATED_AT.plusMinutes(1));
        given(f.paymentClient.findPayment(any(), any(), any())).willReturn(Optional.of(charged(45_000L)));

        assertThatThrownBy(() -> f.useCase.pay(ORDER_ID, BUYER, 5L, CREATED_AT.plusMinutes(2)))
                .isInstanceOf(ServiceException.class);
        then(f.couponClient).should(never()).use(any(), any(), any(), any());
        then(f.couponClient).should(never()).restore(any(), any(), any());
    }

    private static final CouponQuote QUOTE = new CouponQuote(5L, 10L, "전체 10%", 5_000L, true, null);

    private record CouponFixture(OrderPaymentUseCase useCase, OrderPaymentClient paymentClient,
                                 com.cartmate.cartmate.boundedContext.orderService.order.out.OrderCouponClient couponClient,
                                 Order order) {
    }

    private CouponFixture couponFixture() {
        OrderPaymentClient paymentClient = org.mockito.Mockito.mock(OrderPaymentClient.class);
        var couponClient = org.mockito.Mockito.mock(com.cartmate.cartmate.boundedContext.orderService.order.out.OrderCouponClient.class);
        OrderProductClient allOnSale = inventoryIds -> inventoryIds.stream().distinct()
                .map(id -> new OrderProductInfo(id, id, 1L, "상품", null, 1000L, null, true))
                .toList();
        OrderPaymentUseCase useCase = new OrderPaymentUseCase(orderRepository, new OrderSupport(orderRepository),
                eventPublisher, allOnSale, replicaUseCase, paymentClient, couponClient, inventoryClient);
        Order order = personalOrder(BUYER, ballLine(9L, 2), CREATED_AT);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentClient.findPayment(any(), any(), any())).willReturn(Optional.empty());
        return new CouponFixture(useCase, paymentClient, couponClient, order);
    }

    private static PaymentResult charged(long amount) {
        return new PaymentResult(PAYMENT_ID, ORDER_ID, amount, 0L, amount, "INDIVIDUAL_GROUP", "PAID",
                CREATED_AT.plusMinutes(1), null);
    }

    private Order individualGroupOrder(LocalDateTime createdAt) {
        Order order = Order.createIndividualGroup("20261019-IGPORDER", BUYER, PURCHASE_ID, createdAt.plusDays(1),
                List.of(ballLine(9L, 2)), addressWithoutDetail(), createdAt);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        return order;
    }
}
