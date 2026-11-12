package com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared;

import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent.SellerAmount;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderSellerItemsCanceledEvent;
import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.shared.order.SharedGroupOrderCancelReason;
import com.cartmate.cartmate.shared.order.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Stream;

import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.address;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.sharedLine;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.sharedOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SharedGroupOrderCancelUseCaseTest {

    private static final Long ORDER_ID = 100L;
    private static final Long PURCHASE_ID = 10L;
    private static final Long CREATOR = 1L;
    private static final Long MEMBER = 2L;
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 10, 19, 12, 0).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @Mock
    private SharedGroupOrderSupport sharedGroupOrderSupport;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    private SharedGroupOrderCancelUseCase cancelUseCase;

    @BeforeEach
    void setUp() {
        cancelUseCase = new SharedGroupOrderCancelUseCase(
                sharedGroupOrderRepository, sharedGroupOrderSupport, eventPublisher, CLOCK);
    }

    @Test
    @DisplayName("생성자가 취소하면 주문을 취소하고 취소 이벤트를 한 번 발행한다")
    void cancelByCreatorPublishesEvent() {
        SharedGroupOrder order = order(LocalDateTime.now(CLOCK));
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        cancelUseCase.cancelByCreator(CREATOR, ORDER_ID);

        ArgumentCaptor<SharedGroupOrderCanceledEvent> captor = ArgumentCaptor.forClass(SharedGroupOrderCanceledEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        SharedGroupOrderCanceledEvent event = captor.getValue();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CANCELED);
        assertThat(event.sharedGroupPurchaseId()).isEqualTo(PURCHASE_ID);
        assertThat(event.cancelReason()).isEqualTo(SharedGroupOrderCancelReason.CREATOR_CANCELED);
        assertThat(event.canceledAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(event.unpaidMemberIds()).containsExactly(CREATOR, MEMBER);
    }

    @Test
    @DisplayName("생성자가 아니면 취소하지 않고 이벤트도 발행하지 않는다")
    void cancelByNonCreator() {
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order(LocalDateTime.now(CLOCK)));

        assertThatThrownBy(() -> cancelUseCase.cancelByCreator(MEMBER, ORDER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_CREATOR_ONLY.getMsg());
        then(eventPublisher).should(never()).publish(any(DomainEvent.class));
    }

    @Test
    @DisplayName("결제 기한과 유예 시간이 지나면 기한 만료로 취소하고 이벤트를 한 번 발행한다")
    void cancelByDeadlinePublishesEvent() {
        LocalDateTime closedAt = LocalDateTime.now(CLOCK)
                .minus(SharedGroupOrder.PAYMENT_PERIOD)
                .minus(SharedGroupOrder.GRACE_PERIOD)
                .minusMinutes(1);
        SharedGroupOrder order = order(closedAt);
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        cancelUseCase.cancelByDeadline(ORDER_ID);

        ArgumentCaptor<SharedGroupOrderCanceledEvent> captor = ArgumentCaptor.forClass(SharedGroupOrderCanceledEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().cancelReason()).isEqualTo(SharedGroupOrderCancelReason.DEADLINE_EXPIRED);
        assertThat(captor.getValue().canceledAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CANCELED);
    }

    @Test
    @DisplayName("기한 만료 취소 이벤트에는 결제하지 않은 참여자만 담긴다")
    void cancelByDeadlineEventHasOnlyUnpaidMembers() {
        LocalDateTime closedAt = LocalDateTime.now(CLOCK)
                .minus(SharedGroupOrder.PAYMENT_PERIOD)
                .minus(SharedGroupOrder.GRACE_PERIOD)
                .minusMinutes(1);
        SharedGroupOrder order = order(closedAt);
        order.markStockReserved();
        order.pay(CREATOR, 1L, closedAt.plusMinutes(1), address());
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        cancelUseCase.cancelByDeadline(ORDER_ID);

        ArgumentCaptor<SharedGroupOrderCanceledEvent> captor = ArgumentCaptor.forClass(SharedGroupOrderCanceledEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().unpaidMemberIds()).containsExactly(MEMBER);
    }

    @ParameterizedTest(name = "{0}", quoteTextArguments = false)
    @MethodSource("notCancelableByDeadline")
    @DisplayName("기한 만료 취소 대상이 아니면 주문 상태를 바꾸지 않고 이벤트도 발행하지 않는다")
    void cancelByDeadlineSkipped(String situation, SharedGroupOrder order,
                                 SharedGroupOrderStatus expectedStatus, SharedGroupOrderCancelReason expectedReason) {
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        cancelUseCase.cancelByDeadline(ORDER_ID);

        then(eventPublisher).should(never()).publish(any(DomainEvent.class));
        assertThat(order.getStatus()).isEqualTo(expectedStatus);
        assertThat(order.getCancelReason()).isEqualTo(expectedReason);
    }

    static Stream<Arguments> notCancelableByDeadline() {
        LocalDateTime expiredClosedAt = LocalDateTime.now(CLOCK)
                .minus(SharedGroupOrder.PAYMENT_PERIOD)
                .minus(SharedGroupOrder.GRACE_PERIOD)
                .minusMinutes(1);
        SharedGroupOrder alreadyCanceled = order(expiredClosedAt);
        alreadyCanceled.cancelByOutOfStock(LocalDateTime.now(CLOCK));
        return Stream.of(
                Arguments.of("아직 결제 기한 전이면 결제 중으로 남는다",
                        order(LocalDateTime.now(CLOCK)), SharedGroupOrderStatus.PAYING, null),
                Arguments.of("이미 재고 부족으로 취소됐으면 취소 사유가 바뀌지 않는다",
                        alreadyCanceled, SharedGroupOrderStatus.CANCELED, SharedGroupOrderCancelReason.OUT_OF_STOCK));
    }

    @Test
    @DisplayName("재고 부족으로 취소하면 이벤트를 한 번 발행한다")
    void cancelByOutOfStockPublishesEvent() {
        SharedGroupOrder order = order(LocalDateTime.now(CLOCK));
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        cancelUseCase.cancelByOutOfStock(ORDER_ID);

        ArgumentCaptor<SharedGroupOrderCanceledEvent> captor = ArgumentCaptor.forClass(SharedGroupOrderCanceledEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().cancelReason()).isEqualTo(SharedGroupOrderCancelReason.OUT_OF_STOCK);
    }

    private static SharedGroupOrder order(LocalDateTime closedAt) {
        return sharedOrder(PURCHASE_ID, CREATOR, MEMBER, closedAt, sharedLine(2L, 4L, null, 1, 10000L));
    }

    @Test
    @DisplayName("판매자 몫을 취소하면 그 금액과 판매자가 고른 사유를 실은 판매자 몫 취소 이벤트를 발행한다")
    void cancelSellerItemsPublishesReason() {
        SharedGroupOrder order = confirmedTwoSellers();
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        cancelUseCase.cancelSellerItems(ORDER_ID, 7L, DeliveryCancelReason.SELLER_OUT_OF_STOCK);

        then(eventPublisher).should().publish(new SharedGroupOrderSellerItemsCanceledEvent(
                ORDER_ID, 7L, 10_000L, LocalDateTime.now(CLOCK), DeliveryCancelReason.SELLER_OUT_OF_STOCK));
    }

    @Test
    @DisplayName("판매자 몫이 먼저 취소된 뒤 전체 취소되면, 전체 취소 이벤트에 먼저 취소된 판매자 몫(단가 x 수량 합)을 싣는다")
    void wholeCancelCarriesCanceledSellerAmounts() {
        SharedGroupOrder order = confirmedTwoSellers();
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);
        cancelUseCase.cancelSellerItems(ORDER_ID, 8L, DeliveryCancelReason.SELLER_OTHER);

        cancelUseCase.cancelBySalesStopped(ORDER_ID);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        then(eventPublisher).should(org.mockito.Mockito.times(2)).publish(captor.capture());
        SharedGroupOrderCanceledEvent event = (SharedGroupOrderCanceledEvent) captor.getAllValues().get(1);
        assertThat(event.cancelReason()).isEqualTo(SharedGroupOrderCancelReason.SALES_STOPPED);
        assertThat(event.canceledSellerAmounts()).containsExactly(new SellerAmount(8L, 10_000L));
    }

    @Test
    @DisplayName("먼저 취소된 판매자 몫이 없으면 전체 취소 이벤트의 판매자 몫 목록은 빈 목록이다")
    void wholeCancelWithoutCanceledItemsCarriesEmptyList() {
        SharedGroupOrder order = order(LocalDateTime.now(CLOCK));
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        cancelUseCase.cancelByOutOfStock(ORDER_ID);

        ArgumentCaptor<SharedGroupOrderCanceledEvent> captor = ArgumentCaptor.forClass(SharedGroupOrderCanceledEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().canceledSellerAmounts()).isEmpty();
    }

    @Test
    @DisplayName("마지막 판매자 몫까지 취소되면 판매자 몫 이벤트 대신 전체 취소 이벤트를 내고, 목록에는 앞서 취소된 판매자 몫만 싣는다")
    void lastSellerCancelPublishesWholeCancel() {
        SharedGroupOrder order = confirmedTwoSellers();
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);
        cancelUseCase.cancelSellerItems(ORDER_ID, 7L, DeliveryCancelReason.SALES_STOPPED);

        cancelUseCase.cancelSellerItems(ORDER_ID, 8L, DeliveryCancelReason.SELLER_OUT_OF_STOCK);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        then(eventPublisher).should(org.mockito.Mockito.times(2)).publish(captor.capture());
        assertThat(captor.getAllValues().get(0)).isInstanceOf(SharedGroupOrderSellerItemsCanceledEvent.class);
        SharedGroupOrderCanceledEvent event = (SharedGroupOrderCanceledEvent) captor.getAllValues().get(1);
        assertThat(event.cancelReason()).isEqualTo(SharedGroupOrderCancelReason.SELLER_CANCELED);
        assertThat(event.canceledSellerAmounts()).containsExactly(new SellerAmount(7L, 10_000L));
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CANCELED);
    }

    private static SharedGroupOrder confirmedTwoSellers() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        SharedGroupOrder order = sharedOrder(PURCHASE_ID, CREATOR, MEMBER, now,
                sharedLine(2L, 4L, 7L, 1, 10_000L), sharedLine(3L, 5L, 8L, 2, 5_000L));
        order.markStockReserved();
        order.pay(CREATOR, 1L, now, address());
        order.pay(MEMBER, 2L, now, null);
        return order;
    }
}
