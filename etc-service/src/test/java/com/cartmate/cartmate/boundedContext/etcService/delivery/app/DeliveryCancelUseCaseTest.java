package com.cartmate.cartmate.boundedContext.etcService.delivery.app;

import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryOrderState;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.etcService.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.delivery.event.DeliveryOrderCancelRejectedEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliveryOrderCanceledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryCancelUseCaseTest {

    private static final long ORDER_ID = 100L;
    private static final LocalDateTime BEFORE_PREPARE = LocalDateTime.now().minusMinutes(1);
    private static final LocalDateTime AFTER_PREPARE = LocalDateTime.now().plusMinutes(1);

    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    private final DeliveryOrderStates deliveryOrderStates = mock(DeliveryOrderStates.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final DeliveryOrderState state = mock(DeliveryOrderState.class);
    private final DeliveryCancelUseCase useCase =
            new DeliveryCancelUseCase(deliveryRepository, deliveryOrderStates, eventPublisher);

    private void givenState(boolean canceled, boolean inserted, List<Delivery> deliveries) {
        when(state.isCanceled()).thenReturn(canceled);
        when(deliveryOrderStates.lockForCancel(ORDER_ID, PurchaseType.INDIVIDUAL_GROUP))
                .thenReturn(new DeliveryOrderStates.Locked(state, inserted));
        when(deliveryRepository.findAllByOrderIdAndPurchaseTypeForUpdate(ORDER_ID, PurchaseType.INDIVIDUAL_GROUP))
                .thenReturn(deliveries);
    }

    private Delivery delivery(long sellerId) {
        return Delivery.create(7L, sellerId, ORDER_ID, "O-100", PurchaseType.INDIVIDUAL_GROUP,
                "수령인", "010", "00000", "서울", "101호", "key-" + sellerId, "hash");
    }

    @Test
    @DisplayName("이미 취소된 주문의 요청이 다시 오면 배송을 보지 않고 취소됨을 다시 보낸다")
    void alreadyCanceledRepliesAgain() {
        givenState(true, false, List.of());

        useCase.cancelByBuyerRequest(ORDER_ID, 2, BEFORE_PREPARE);

        verify(eventPublisher).publish(new DeliveryOrderCanceledEvent(ORDER_ID, 2));
        verify(deliveryRepository, never()).findAllByOrderIdAndPurchaseTypeForUpdate(anyLong(), any());
    }

    @Test
    @DisplayName("배송이 아직 없으면 취소 표식만 남기고 취소됨을 보낸다")
    void noDeliveryYetIsCanceled() {
        givenState(true, true, List.of());

        useCase.cancelByBuyerRequest(ORDER_ID, 1, BEFORE_PREPARE);

        verify(state).markCanceled();
        verify(eventPublisher).publish(new DeliveryOrderCanceledEvent(ORDER_ID, 1));
    }

    @Test
    @DisplayName("모든 배송을 취소할 수 있으면 전부 취소하고 취소됨을 보낸다")
    void cancelsAllWhenAllowed() {
        Delivery requested = delivery(1007L);
        Delivery preparedAfterRequest = delivery(1009L);
        preparedAfterRequest.prepare("CJ", "123");
        givenState(false, false, List.of(requested, preparedAfterRequest));

        useCase.cancelByBuyerRequest(ORDER_ID, 1, BEFORE_PREPARE);

        assertThat(requested.getStatus()).isEqualTo(DeliveryStatus.CANCELED);
        assertThat(preparedAfterRequest.getStatus()).isEqualTo(DeliveryStatus.CANCELED);
        verify(state).markCanceled();
        verify(eventPublisher).publish(new DeliveryOrderCanceledEvent(ORDER_ID, 1));
    }

    @Test
    @DisplayName("하나라도 요청 전에 확인된 배송이 있으면 아무것도 취소하지 않고 거절한다")
    void rejectsAllWhenAnyNotCancelable() {
        Delivery requested = delivery(1007L);
        Delivery preparedBeforeRequest = delivery(1009L);
        preparedBeforeRequest.prepare("CJ", "123");
        givenState(false, false, List.of(requested, preparedBeforeRequest));

        useCase.cancelByBuyerRequest(ORDER_ID, 1, AFTER_PREPARE);

        assertThat(requested.getStatus()).isEqualTo(DeliveryStatus.REQUESTED);
        verify(state).markCreated();
        verify(state, never()).markCanceled();
        verify(eventPublisher).publish(new DeliveryOrderCancelRejectedEvent(ORDER_ID, 1, "409-DELIVERY_NOT_CANCELABLE"));
    }

    @Test
    @DisplayName("요청 뒤에 확인되어 이미 발송된 배송이 있으면 처리 지연으로 거절한다")
    void rejectsAsDelayedWhenShippedAfterRequest() {
        Delivery shipped = delivery(1007L);
        shipped.prepare("CJ", "123");
        shipped.ship();
        givenState(false, false, List.of(shipped));

        useCase.cancelByBuyerRequest(ORDER_ID, 3, BEFORE_PREPARE);

        verify(eventPublisher).publish(new DeliveryOrderCancelRejectedEvent(ORDER_ID, 3, "409-DELIVERY_CANCEL_DELAYED"));
    }
}
