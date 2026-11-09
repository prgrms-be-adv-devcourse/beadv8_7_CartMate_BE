package com.cartmate.cartmate.boundedContext.orderService.order.app.salesstop;

import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.salesstop.SalesStopCancelRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.salesstop.SalesStopCancelRequestStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.out.salesstop.SalesStopCancelRequestRepository;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.delivery.SalesStopCancelResult;
import com.cartmate.cartmate.shared.delivery.event.DeliverySalesStopCancelResultEvent;
import com.cartmate.cartmate.shared.order.event.OrderSalesStopCancelRequestedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SalesStopCancelFacadeTest {

    private static final long ORDER_ID = 100L;
    private static final long SELLER_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 12, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    private static final Duration RESEND_AFTER = Duration.ofMinutes(10);

    private final SalesStopCancelRequestRepository repository = mock(SalesStopCancelRequestRepository.class);
    private final OrderFacade orderFacade = mock(OrderFacade.class);
    private final SharedGroupOrderFacade sharedGroupOrderFacade = mock(SharedGroupOrderFacade.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final SalesStopCancelFacade facade =
            new SalesStopCancelFacade(repository, orderFacade, sharedGroupOrderFacade, eventPublisher, CLOCK);

    private SalesStopCancelRequest request(PurchaseType purchaseType, SalesStopCancelRequestStatus status,
                                           LocalDateTime lastSentAt) {
        SalesStopCancelRequest request = BeanUtils.instantiateClass(SalesStopCancelRequest.class);
        ReflectionTestUtils.setField(request, "purchaseType", purchaseType);
        ReflectionTestUtils.setField(request, "orderId", ORDER_ID);
        ReflectionTestUtils.setField(request, "sellerId", SELLER_ID);
        ReflectionTestUtils.setField(request, "status", status);
        ReflectionTestUtils.setField(request, "requestedAt", NOW.minusHours(1));
        ReflectionTestUtils.setField(request, "lastSentAt", lastSentAt);
        return request;
    }

    private SalesStopCancelRequest givenRequested(PurchaseType purchaseType) {
        SalesStopCancelRequest request = request(purchaseType, SalesStopCancelRequestStatus.REQUESTED, NOW);
        when(repository.findForUpdate(purchaseType, ORDER_ID, SELLER_ID)).thenReturn(Optional.of(request));
        return request;
    }

    private DeliverySalesStopCancelResultEvent result(PurchaseType purchaseType, SalesStopCancelResult result, String code) {
        return new DeliverySalesStopCancelResultEvent(purchaseType, ORDER_ID, SELLER_ID, result, code);
    }

    @Test
    @DisplayName("처음 요청하는 판매자 몫이면 요청 장부에 넣고 배송 서버에 취소 요청을 보낸다")
    void requestPublishesOnce() {
        when(repository.insertIfAbsent("INDIVIDUAL_GROUP", ORDER_ID, SELLER_ID, NOW)).thenReturn(1);

        facade.request(PurchaseType.INDIVIDUAL_GROUP, ORDER_ID, SELLER_ID);

        verify(eventPublisher).publish(
                new OrderSalesStopCancelRequestedEvent(PurchaseType.INDIVIDUAL_GROUP, ORDER_ID, SELLER_ID, NOW));
    }

    @Test
    @DisplayName("이미 요청한 판매자 몫이면 다시 보내지 않는다 (판매글을 다시 올렸다 내려도 같음)")
    void requestSkipsExisting() {
        when(repository.insertIfAbsent("INDIVIDUAL_GROUP", ORDER_ID, SELLER_ID, NOW)).thenReturn(0);

        facade.request(PurchaseType.INDIVIDUAL_GROUP, ORDER_ID, SELLER_ID);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("그 판매자 몫 취소됨을 받으면 개인 주문의 그 판매자 항목을 취소하고 요청을 끝낸다")
    void sellerCanceledCancelsPersonalItems() {
        SalesStopCancelRequest request = givenRequested(PurchaseType.INDIVIDUAL_GROUP);

        facade.applyResult(result(PurchaseType.INDIVIDUAL_GROUP, SalesStopCancelResult.SELLER_CANCELED, null));

        verify(orderFacade).cancelSellerItemsBySalesStopped(ORDER_ID, SELLER_ID);
        assertThat(request.getStatus()).isEqualTo(SalesStopCancelRequestStatus.CANCELED);
    }

    @Test
    @DisplayName("전체 취소됨을 받으면 공동구매 전체를 취소한다")
    void wholeCanceledCancelsSharedOrder() {
        SalesStopCancelRequest request = givenRequested(PurchaseType.SHARED_GROUP);

        facade.applyResult(result(PurchaseType.SHARED_GROUP, SalesStopCancelResult.WHOLE_CANCELED, null));

        verify(sharedGroupOrderFacade).cancelWholeBySalesStopResult(ORDER_ID);
        verify(sharedGroupOrderFacade, never()).cancelSellerItemsBySalesStopped(anyLong(), anyLong());
        assertThat(request.getStatus()).isEqualTo(SalesStopCancelRequestStatus.CANCELED);
    }

    @Test
    @DisplayName("공동구매의 그 판매자 몫 취소됨을 받으면 그 판매자 항목만 취소한다")
    void sellerCanceledCancelsSharedItems() {
        givenRequested(PurchaseType.SHARED_GROUP);

        facade.applyResult(result(PurchaseType.SHARED_GROUP, SalesStopCancelResult.SELLER_CANCELED, null));

        verify(sharedGroupOrderFacade).cancelSellerItemsBySalesStopped(ORDER_ID, SELLER_ID);
    }

    @Test
    @DisplayName("거절을 받으면 항목은 그대로 두고 요청을 거절로 닫는다")
    void rejectedKeepsItems() {
        SalesStopCancelRequest request = givenRequested(PurchaseType.INDIVIDUAL_GROUP);

        facade.applyResult(result(PurchaseType.INDIVIDUAL_GROUP, SalesStopCancelResult.REJECTED, "409-DELIVERY_NOT_CANCELABLE"));

        verifyNoInteractions(orderFacade, sharedGroupOrderFacade);
        assertThat(request.getStatus()).isEqualTo(SalesStopCancelRequestStatus.REJECTED);
        assertThat(request.getRejectCode()).isEqualTo("409-DELIVERY_NOT_CANCELABLE");
    }

    @Test
    @DisplayName("이미 끝난 요청에 결과가 다시 오면 반영하지 않는다 (재발행으로 온 중복 결과)")
    void ignoresResultOfFinishedRequest() {
        SalesStopCancelRequest done = request(PurchaseType.INDIVIDUAL_GROUP, SalesStopCancelRequestStatus.CANCELED, NOW);
        when(repository.findForUpdate(PurchaseType.INDIVIDUAL_GROUP, ORDER_ID, SELLER_ID)).thenReturn(Optional.of(done));

        facade.applyResult(result(PurchaseType.INDIVIDUAL_GROUP, SalesStopCancelResult.SELLER_CANCELED, null));

        verifyNoInteractions(orderFacade);
    }

    @Test
    @DisplayName("마지막 전송 뒤 간격이 지났으면 처음 요청 시각 그대로 다시 보낸다")
    void resendsWhenDue() {
        SalesStopCancelRequest request = request(PurchaseType.INDIVIDUAL_GROUP, SalesStopCancelRequestStatus.REQUESTED,
                NOW.minus(RESEND_AFTER));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(request));

        facade.resend(1L, RESEND_AFTER);

        verify(eventPublisher).publish(new OrderSalesStopCancelRequestedEvent(
                PurchaseType.INDIVIDUAL_GROUP, ORDER_ID, SELLER_ID, NOW.minusHours(1)));
        assertThat(request.getLastSentAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("간격이 아직 안 지났으면 다시 보내지 않는다")
    void skipsResendBeforeInterval() {
        SalesStopCancelRequest request = request(PurchaseType.INDIVIDUAL_GROUP, SalesStopCancelRequestStatus.REQUESTED,
                NOW.minusMinutes(9));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(request));

        facade.resend(1L, RESEND_AFTER);

        verify(eventPublisher, never()).publish(any());
    }
}
