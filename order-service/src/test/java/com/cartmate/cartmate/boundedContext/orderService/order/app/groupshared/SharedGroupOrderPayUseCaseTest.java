package com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderPaymentClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.orderService.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalServerUnavailableException;
import com.cartmate.cartmate.shared.order.SharedGroupOrderStatus;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderConfirmedEvent;
import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.sharedOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class SharedGroupOrderPayUseCaseTest {

    private static final Long ORDER_ID = 100L;
    private static final Long PURCHASE_ID = 10L;
    private static final Long CREATOR = 1L;
    private static final Long MEMBER = 2L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0);
    private static final TeamDeliveryAddress ADDRESS =
            new TeamDeliveryAddress("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호");

    @Mock
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private OrderPaymentClient paymentClient;

    private SharedGroupOrderPaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = useCase(SharedGroupOrderPayUseCaseTest::allOnSale);
    }

    @Test
    @DisplayName("두 참여자가 차례로 결제하면 각자 몫만큼만 차감하고, 마지막 결제에서 주문을 확정해 확정 이벤트를 한 번 발행한다")
    void payEachShareAndConfirmOnLast() {
        SharedGroupOrder order = order(NOW);
        givenOrder(order);
        given(paymentClient.findPayment(any(), any(), any())).willReturn(Optional.empty());
        given(paymentClient.charge(PaymentOrderType.SHARED_GROUP, ORDER_ID, CREATOR, 15001L))
                .willReturn(paid(501L, 15001L, NOW.plusMinutes(1)));
        given(paymentClient.charge(PaymentOrderType.SHARED_GROUP, ORDER_ID, MEMBER, 15000L))
                .willReturn(paid(502L, 15000L, NOW.plusMinutes(2)));

        useCase.pay(ORDER_ID, CREATOR, ADDRESS, NOW.plusMinutes(1));

        assertThat(order.getParticipant(CREATOR).isPaid()).isTrue();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.PAYING);
        then(eventPublisher).should(never()).publish(any());

        useCase.pay(ORDER_ID, MEMBER, null, NOW.plusMinutes(2));

        ArgumentCaptor<SharedGroupOrderConfirmedEvent> captor = ArgumentCaptor.forClass(SharedGroupOrderConfirmedEvent.class);
        then(eventPublisher).should(times(1)).publish(captor.capture());
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CONFIRMED);
        assertThat(captor.getValue().confirmedAt()).isEqualTo(NOW.plusMinutes(2));
        assertThat(captor.getValue().teamDeliveryAddress()).isEqualTo(ADDRESS);
        then(paymentClient).should(never()).compensateCharge(any(), any(), any());
    }

    @Test
    @DisplayName("이미 차감된 결제가 있으면 다시 차감하지 않고 그 결제를 반영만 한다 (반영 실패 뒤 재시도)")
    void existingPaidPaymentIsAppliedWithoutSecondCharge() {
        SharedGroupOrder order = order(NOW);
        order.markStockReserved();
        givenOrderForUpdate(order);
        PaymentResult existing = paid(502L, 15000L, NOW.plusMinutes(1));
        given(paymentClient.findPayment(PaymentOrderType.SHARED_GROUP, ORDER_ID, MEMBER)).willReturn(Optional.of(existing));

        PaymentResult result = useCase.pay(ORDER_ID, MEMBER, null, NOW.plusMinutes(5));

        assertThat(result).isSameAs(existing);
        assertThat(order.getParticipant(MEMBER).getPaymentId()).isEqualTo(502L);
        then(paymentClient).should(never()).charge(any(), any(), any(), anyLong());
        then(paymentClient).should(never()).compensateCharge(any(), any(), any());
    }

    @Test
    @DisplayName("이미 취소된 공동 주문에 차감된 결제가 늦게 반영되면 상품 서버를 부르지 않고 차감을 되돌리며 참여자는 미결제로 남는다")
    void lateExistingPaymentOnCanceledOrderIsCompensated() {
        useCase = useCase(ids -> {
            throw new InternalServerUnavailableException("PRODUCT", "상품", null);
        });
        SharedGroupOrder order = order(NOW);
        order.markStockReserved();
        order.cancelByCreator(CREATOR, NOW.plusMinutes(1));
        givenOrderForUpdate(order);
        given(paymentClient.findPayment(PaymentOrderType.SHARED_GROUP, ORDER_ID, MEMBER))
                .willReturn(Optional.of(paid(502L, 15000L, NOW.plusMinutes(2))));

        assertThatThrownBy(() -> useCase.pay(ORDER_ID, MEMBER, null, NOW.plusMinutes(2)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NOT_PAYABLE.getMsg());
        then(paymentClient).should().compensateCharge(PaymentOrderType.SHARED_GROUP, ORDER_ID, MEMBER);
        then(paymentClient).should(never()).charge(any(), any(), any(), anyLong());
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CANCELED);
        assertThat(order.getParticipant(MEMBER).isPaid()).isFalse();
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("이전 결제가 되돌려진 상태로 남아 있으면 결제된 것으로 보지 않고 새로 차감한다")
    void notPaidExistingPaymentChargesAgain() {
        SharedGroupOrder order = order(NOW);
        order.markStockReserved();
        givenOrder(order);
        given(paymentClient.findPayment(PaymentOrderType.SHARED_GROUP, ORDER_ID, MEMBER))
                .willReturn(Optional.of(new PaymentResult(501L, ORDER_ID, 15000L, 15000L, 0L, "SHARED_GROUP",
                        "CANCELED", NOW, NOW.plusMinutes(1))));
        given(paymentClient.charge(PaymentOrderType.SHARED_GROUP, ORDER_ID, MEMBER, 15000L))
                .willReturn(paid(502L, 15000L, NOW.plusMinutes(2)));

        useCase.pay(ORDER_ID, MEMBER, null, NOW.plusMinutes(2));

        assertThat(order.getParticipant(MEMBER).getPaymentId()).isEqualTo(502L);
    }

    @Test
    @DisplayName("결제 기한이 지났으면 차감하지 않고 재고 선점도 표시하지 않는다")
    void afterDeadlineNoCharge() {
        SharedGroupOrder order = order(NOW);
        given(sharedGroupOrderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentClient.findPayment(any(), any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.pay(ORDER_ID, MEMBER, null,
                order.getPaymentDeadlineAt().plusSeconds(1)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NOT_PAYABLE.getMsg());
        then(paymentClient).should(never()).charge(any(), any(), any(), anyLong());
        assertThat(order.isStockReserved()).isFalse();
    }

    @Test
    @DisplayName("생성자가 팀 배송지 없이 결제하면 차감 전에 거절한다")
    void creatorWithoutAddressRejectedBeforeCharge() {
        SharedGroupOrder order = order(NOW);
        given(sharedGroupOrderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentClient.findPayment(any(), any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.pay(ORDER_ID, CREATOR, null, NOW.plusMinutes(1)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_TEAM_ADDRESS_REQUIRED.getMsg());
        then(paymentClient).should(never()).charge(any(), any(), any(), anyLong());
        assertThat(order.isStockReserved()).isFalse();
    }

    @Test
    @DisplayName("구매할 수 없는 상품이 있으면 차감하지 않고 재고 선점도 표시하지 않는다")
    void notPurchasableRejectedBeforeCharge() {
        useCase = useCase(ids -> ids.stream().distinct()
                .map(id -> new OrderProductInfo(id, id, 1L, "상품", null, 1000L, null, !id.equals(5L)))
                .toList());
        SharedGroupOrder order = order(NOW);
        given(sharedGroupOrderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentClient.findPayment(any(), any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.pay(ORDER_ID, MEMBER, null, NOW.plusMinutes(1)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_PRODUCT_NOT_PURCHASABLE.getMsg());
        then(paymentClient).should(never()).charge(any(), any(), any(), anyLong());
        assertThat(order.isStockReserved()).isFalse();
    }

    @Test
    @DisplayName("차감에 응답이 없으면 되돌리기 · 반영 · 이벤트 없이 503을 그대로 던지고 참여자는 미결제로 남는다")
    void chargeUnavailableLeavesUnpaid() {
        assertChargeFailureLeavesUnpaid(new InternalServerUnavailableException("PAYMENT", "결제", null));
    }

    @Test
    @DisplayName("결제 서버가 차감을 업무 거절하면 되돌리기 · 반영 · 이벤트 없이 그 예외를 그대로 던지고 참여자는 미결제로 남는다")
    void chargeRejectedLeavesUnpaid() {
        assertChargeFailureLeavesUnpaid(new ServiceException("400-INSUFFICIENT_BALANCE", "예치금이 부족합니다."));
    }

    @Test
    @DisplayName("차감 뒤 반영 중 상품 서버가 잠깐 503이면 차감을 되돌리지 않고 그대로 던진다 — 다시 결제하면 반영만 한다")
    void doNotCompensateOnTemporaryOutage() {
        AtomicInteger calls = new AtomicInteger();
        useCase = useCase(ids -> {
            if (calls.incrementAndGet() > 1) {
                throw new InternalServerUnavailableException("PRODUCT", "상품", null);
            }
            return allOnSale(ids);
        });
        SharedGroupOrder order = order(NOW);
        givenOrder(order);
        given(paymentClient.findPayment(any(), any(), any())).willReturn(Optional.empty());
        given(paymentClient.charge(any(), any(), any(), anyLong())).willReturn(paid(502L, 15000L, NOW.plusMinutes(1)));

        assertThatThrownBy(() -> useCase.pay(ORDER_ID, MEMBER, null, NOW.plusMinutes(1)))
                .isInstanceOf(InternalServerUnavailableException.class);
        then(paymentClient).should(never()).compensateCharge(any(), any(), any());
        assertThat(order.getParticipant(MEMBER).isPaid()).isFalse();
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("차감 뒤 반영 중 상품이 판매 불가로 바뀌어 업무 거절되면 차감을 되돌리고 참여자는 미결제로 남는다")
    void compensateWhenProductBecomesNotPurchasableAfterCharge() {
        AtomicInteger calls = new AtomicInteger();
        useCase = useCase(ids -> {
            boolean onSale = calls.incrementAndGet() == 1;
            return ids.stream().distinct()
                    .map(id -> new OrderProductInfo(id, id, 1L, "상품", null, 1000L, null, onSale))
                    .toList();
        });
        SharedGroupOrder order = order(NOW);
        givenOrder(order);
        given(paymentClient.findPayment(any(), any(), any())).willReturn(Optional.empty());
        given(paymentClient.charge(any(), any(), any(), anyLong())).willReturn(paid(502L, 15000L, NOW.plusMinutes(1)));

        assertThatThrownBy(() -> useCase.pay(ORDER_ID, MEMBER, null, NOW.plusMinutes(1)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_PRODUCT_NOT_PURCHASABLE.getMsg());
        then(paymentClient).should().compensateCharge(PaymentOrderType.SHARED_GROUP, ORDER_ID, MEMBER);
        assertThat(order.getParticipant(MEMBER).isPaid()).isFalse();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.PAYING);
        then(eventPublisher).should(never()).publish(any());
    }

    private void assertChargeFailureLeavesUnpaid(ServiceException failure) {
        SharedGroupOrder order = order(NOW);
        given(sharedGroupOrderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(sharedGroupOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentClient.findPayment(any(), any(), any())).willReturn(Optional.empty());
        given(paymentClient.charge(any(), any(), any(), anyLong())).willThrow(failure);

        assertThatThrownBy(() -> useCase.pay(ORDER_ID, MEMBER, null, NOW.plusMinutes(1))).isSameAs(failure);
        then(paymentClient).should(never()).compensateCharge(any(), any(), any());
        then(eventPublisher).should(never()).publish(any());
        assertThat(order.getParticipant(MEMBER).isPaid()).isFalse();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.PAYING);
    }

    @Test
    @DisplayName("이미 차감된 생성자가 팀 배송지 없이 다시 결제하면 차감을 되돌리지 않고 배송지를 요구한다 — 배송지를 넣어 다시 하면 그 결제를 반영만 한다 (ISS-30)")
    void creatorRetryWithoutAddressKeepsCharge() {
        SharedGroupOrder order = order(NOW);
        order.markStockReserved();
        givenOrderForUpdate(order);
        given(paymentClient.findPayment(PaymentOrderType.SHARED_GROUP, ORDER_ID, CREATOR))
                .willReturn(Optional.of(paid(501L, 15001L, NOW.plusMinutes(1))));

        assertThatThrownBy(() -> useCase.pay(ORDER_ID, CREATOR, null, NOW.plusMinutes(2)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_TEAM_ADDRESS_REQUIRED.getMsg());
        then(paymentClient).should(never()).compensateCharge(any(), any(), any());
        assertThat(order.getParticipant(CREATOR).isPaid()).isFalse();

        useCase.pay(ORDER_ID, CREATOR, ADDRESS, NOW.plusMinutes(3));

        then(paymentClient).should(never()).charge(any(), any(), any(), anyLong());
        then(paymentClient).should(never()).compensateCharge(any(), any(), any());
        assertThat(order.getParticipant(CREATOR).getPaymentId()).isEqualTo(501L);
    }

    private SharedGroupOrderPaymentUseCase useCase(OrderProductClient productClient) {
        return new SharedGroupOrderPaymentUseCase(sharedGroupOrderRepository,
                new SharedGroupOrderSupport(sharedGroupOrderRepository), eventPublisher, productClient, paymentClient);
    }

    private static List<OrderProductInfo> allOnSale(Collection<Long> inventoryIds) {
        return inventoryIds.stream().distinct()
                .map(id -> new OrderProductInfo(id, id, 1L, "상품", null, 1000L, null, true))
                .toList();
    }

    private void givenOrder(SharedGroupOrder order) {
        given(sharedGroupOrderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        givenOrderForUpdate(order);
    }

    private void givenOrderForUpdate(SharedGroupOrder order) {
        given(sharedGroupOrderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
    }

    private PaymentResult paid(Long paymentId, long amount, LocalDateTime paidAt) {
        return new PaymentResult(paymentId, ORDER_ID, amount, 0L, amount, "SHARED_GROUP", "PAID", paidAt, null);
    }

    private SharedGroupOrder order(LocalDateTime closedAt) {
        return sharedOrder(PURCHASE_ID, CREATOR, MEMBER, closedAt,
                new OrderItemLine(2L, 4L, 7L, "축구공", "5호", 1, 10001L),
                new OrderItemLine(3L, 5L, 8L, "축구 유니폼", null, 2, 10000L));
    }
}
