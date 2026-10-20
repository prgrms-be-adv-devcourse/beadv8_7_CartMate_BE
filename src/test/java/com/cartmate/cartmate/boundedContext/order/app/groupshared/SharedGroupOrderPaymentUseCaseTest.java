package com.cartmate.cartmate.boundedContext.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.dto.SharedGroupOrderPaymentInfo;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderConfirmedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class SharedGroupOrderPaymentUseCaseTest {

    private static final Long ORDER_ID = 100L;
    private static final Long PURCHASE_ID = 10L;
    private static final Long CREATOR = 1L;
    private static final Long MEMBER = 2L;
    private static final Long OUTSIDER = 3L;
    private static final Long CREATOR_PAYMENT_ID = 501L;
    private static final Long MEMBER_PAYMENT_ID = 502L;
    private static final TeamDeliveryAddress ADDRESS =
            new TeamDeliveryAddress("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호");

    @Mock
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    @Mock
    private SharedGroupOrderSupport sharedGroupOrderSupport;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SharedGroupOrderPaymentUseCase paymentUseCase;

    @Test
    @DisplayName("결제 정보는 생성자에게 팀 배송지 입력과 재고 선점 필요 여부를 알려준다")
    void paymentInfoForCreator() {
        SharedGroupOrder order = order(LocalDateTime.now());
        given(sharedGroupOrderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        SharedGroupOrderPaymentInfo info = paymentUseCase.getPaymentInfo(ORDER_ID, CREATOR, LocalDateTime.now());

        assertThat(info.memberId()).isEqualTo(CREATOR);
        assertThat(info.shareAmount()).isEqualTo(15000L);
        assertThat(info.payable()).isTrue();
        assertThat(info.needsStockReservation()).isTrue();
        assertThat(info.teamAddressRequired()).isTrue();
        assertThat(info.paymentDeadlineAt()).isEqualTo(order.getPaymentDeadlineAt());
    }

    @Test
    @DisplayName("이미 결제한 참여자는 결제할 수 없고, 재고를 선점했으면 선점이 필요 없다고 알려준다")
    void paymentInfoForPaidMember() {
        SharedGroupOrder order = order(LocalDateTime.now());
        order.markStockReserved();
        order.pay(MEMBER, MEMBER_PAYMENT_ID, LocalDateTime.now(), null);
        given(sharedGroupOrderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        SharedGroupOrderPaymentInfo info = paymentUseCase.getPaymentInfo(ORDER_ID, MEMBER, LocalDateTime.now());

        assertThat(info.shareAmount()).isEqualTo(15000L);
        assertThat(info.payable()).isFalse();
        assertThat(info.needsStockReservation()).isFalse();
        assertThat(info.teamAddressRequired()).isFalse();
    }

    @Test
    @DisplayName("결제 기한이 지나면 결제할 수 없다고 알려준다")
    void paymentInfoAfterDeadline() {
        SharedGroupOrder order = order(expiredClosedAt());
        given(sharedGroupOrderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        assertThat(paymentUseCase.getPaymentInfo(ORDER_ID, MEMBER, LocalDateTime.now()).payable()).isFalse();
    }

    @Test
    @DisplayName("참여자가 아니면 결제 정보를 조회할 수 없다")
    void paymentInfoForOutsider() {
        given(sharedGroupOrderRepository.findById(ORDER_ID)).willReturn(Optional.of(order(LocalDateTime.now())));

        assertThatThrownBy(() -> paymentUseCase.getPaymentInfo(ORDER_ID, OUTSIDER, LocalDateTime.now()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NOT_PARTICIPANT.getMsg());
    }

    @Test
    @DisplayName("없는 공동 주문의 결제 정보는 조회할 수 없다")
    void paymentInfoNotFound() {
        given(sharedGroupOrderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentUseCase.getPaymentInfo(ORDER_ID, CREATOR, LocalDateTime.now()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NOT_FOUND.getMsg());
    }

    @Test
    @DisplayName("재고 선점 표시는 여러 번 호출해도 한 번 표시한 것과 같다")
    void markStockReservedIdempotent() {
        SharedGroupOrder order = order(LocalDateTime.now());
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        paymentUseCase.markStockReserved(ORDER_ID);
        paymentUseCase.markStockReserved(ORDER_ID);

        assertThat(order.isStockReserved()).isTrue();
        assertThat(order.needsStockReservation()).isFalse();
    }

    @Test
    @DisplayName("결제 중이 아닌 공동 주문에는 재고 선점을 표시할 수 없다")
    void markStockReservedNotPaying() {
        SharedGroupOrder order = order(LocalDateTime.now());
        order.cancelByOutOfStock(LocalDateTime.now());
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        assertThatThrownBy(() -> paymentUseCase.markStockReserved(ORDER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NOT_PAYING.getMsg());
        assertThat(order.isStockReserved()).isFalse();
    }

    @Test
    @DisplayName("마지막 결제가 아니면 결제만 반영하고 확정 이벤트는 발행하지 않는다")
    void applyPaymentNotFinal() {
        SharedGroupOrder order = reservedOrder(LocalDateTime.now());
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        paymentUseCase.applyPayment(ORDER_ID, MEMBER, MEMBER_PAYMENT_ID, LocalDateTime.now(), null);

        assertThat(order.getParticipant(MEMBER).isPaid()).isTrue();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.PAYING);
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("마지막 결제면 주문을 확정하고 확정 이벤트를 한 번 발행한다")
    void applyPaymentFinal() {
        SharedGroupOrder order = reservedOrder(LocalDateTime.now());
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);
        LocalDateTime paidAt = LocalDateTime.now();

        paymentUseCase.applyPayment(ORDER_ID, MEMBER, MEMBER_PAYMENT_ID, paidAt.minusMinutes(1), null);
        paymentUseCase.applyPayment(ORDER_ID, CREATOR, CREATOR_PAYMENT_ID, paidAt, ADDRESS);

        ArgumentCaptor<SharedGroupOrderConfirmedEvent> captor = ArgumentCaptor.forClass(SharedGroupOrderConfirmedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        SharedGroupOrderConfirmedEvent event = captor.getValue();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CONFIRMED);
        assertThat(event.sharedGroupPurchaseId()).isEqualTo(PURCHASE_ID);
        assertThat(event.creatorMemberId()).isEqualTo(CREATOR);
        assertThat(event.confirmedAt()).isEqualTo(paidAt);
        assertThat(event.teamDeliveryAddress()).isEqualTo(ADDRESS);
        assertThat(event.items())
                .extracting(SharedGroupOrderConfirmedEvent.Item::productId, SharedGroupOrderConfirmedEvent.Item::inventoryId,
                        SharedGroupOrderConfirmedEvent.Item::sellerId, SharedGroupOrderConfirmedEvent.Item::productName,
                        SharedGroupOrderConfirmedEvent.Item::optionName,
                        SharedGroupOrderConfirmedEvent.Item::quantity, SharedGroupOrderConfirmedEvent.Item::unitPrice)
                .containsExactly(tuple(2L, 4L, 7L, "축구공", "5호", 1, 10000L), tuple(3L, 5L, 8L, "축구 유니폼", null, 2, 10000L));
    }

    @Test
    @DisplayName("같은 결제 ID로 결제 통보가 다시 와도 예외 없이 확정 이벤트는 한 번만 발행한다")
    void applyPaymentSamePaymentIdTwice() {
        SharedGroupOrder order = reservedOrder(LocalDateTime.now());
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);
        LocalDateTime paidAt = LocalDateTime.now();

        paymentUseCase.applyPayment(ORDER_ID, MEMBER, MEMBER_PAYMENT_ID, paidAt, null);
        paymentUseCase.applyPayment(ORDER_ID, MEMBER, MEMBER_PAYMENT_ID, paidAt, null);
        paymentUseCase.applyPayment(ORDER_ID, CREATOR, CREATOR_PAYMENT_ID, paidAt, ADDRESS);
        paymentUseCase.applyPayment(ORDER_ID, CREATOR, CREATOR_PAYMENT_ID, paidAt, ADDRESS);

        then(eventPublisher).should(times(1)).publishEvent(any(SharedGroupOrderConfirmedEvent.class));
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CONFIRMED);
        assertThat(order.getParticipant(MEMBER).getPaymentId()).isEqualTo(MEMBER_PAYMENT_ID);
    }

    @Test
    @DisplayName("이미 다른 결제 ID로 결제한 참여자는 다시 결제할 수 없다")
    void applyPaymentDifferentPaymentId() {
        SharedGroupOrder order = reservedOrder(LocalDateTime.now());
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);
        paymentUseCase.applyPayment(ORDER_ID, MEMBER, MEMBER_PAYMENT_ID, LocalDateTime.now(), null);

        assertThatThrownBy(() -> paymentUseCase.applyPayment(ORDER_ID, MEMBER, MEMBER_PAYMENT_ID + 100, LocalDateTime.now(), null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_ALREADY_PAID.getMsg());
        assertThat(order.getParticipant(MEMBER).getPaymentId()).isEqualTo(MEMBER_PAYMENT_ID);
    }

    @Test
    @DisplayName("결제 기한이 지난 결제는 반영하지 않고 이벤트도 발행하지 않는다")
    void applyPaymentLate() {
        SharedGroupOrder order = reservedOrder(expiredClosedAt());
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        assertThatThrownBy(() -> paymentUseCase.applyPayment(ORDER_ID, MEMBER, MEMBER_PAYMENT_ID, LocalDateTime.now(), null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NOT_PAYABLE.getMsg());
        assertThat(order.getParticipant(MEMBER).isPaid()).isFalse();
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("생성자가 팀 배송지 없이 결제하면 반영하지 않는다")
    void applyPaymentCreatorWithoutAddress() {
        SharedGroupOrder order = reservedOrder(LocalDateTime.now());
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        assertThatThrownBy(() -> paymentUseCase.applyPayment(ORDER_ID, CREATOR, CREATOR_PAYMENT_ID, LocalDateTime.now(), null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_TEAM_ADDRESS_REQUIRED.getMsg());
        assertThat(order.getParticipant(CREATOR).isPaid()).isFalse();
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("재고를 선점하지 않았으면 결제를 반영하지 않는다")
    void applyPaymentStockNotReserved() {
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order(LocalDateTime.now()));

        assertThatThrownBy(() -> paymentUseCase.applyPayment(ORDER_ID, MEMBER, MEMBER_PAYMENT_ID, LocalDateTime.now(), null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_STOCK_NOT_RESERVED.getMsg());
    }

    private LocalDateTime expiredClosedAt() {
        return LocalDateTime.now()
                .minus(SharedGroupOrder.PAYMENT_PERIOD)
                .minus(SharedGroupOrder.GRACE_PERIOD)
                .minusMinutes(1);
    }

    private SharedGroupOrder reservedOrder(LocalDateTime closedAt) {
        SharedGroupOrder order = order(closedAt);
        order.markStockReserved();
        return order;
    }

    private SharedGroupOrder order(LocalDateTime closedAt) {
        return SharedGroupOrder.create(PURCHASE_ID, CREATOR, List.of(CREATOR, MEMBER),
                List.of(new OrderItemLine(2L, 4L, 7L, "축구공", "5호", 1, 10000L),
                        new OrderItemLine(3L, 5L, 8L, "축구 유니폼", null, 2, 10000L)), closedAt);
    }
}
