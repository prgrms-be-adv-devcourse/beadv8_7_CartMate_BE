package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimItemRepository.ClaimedQuantity;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemRepository;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryConfirmedEventPublisherTest {

    private final DeliveryItemRepository deliveryItemRepository = mock(DeliveryItemRepository.class);
    private final DeliveryClaimItemRepository deliveryClaimItemRepository = mock(DeliveryClaimItemRepository.class);
    private final EventPublisher eventPublisher = mock(EventPublisher.class);
    private final DeliveryConfirmedEventPublisher publisher =
            new DeliveryConfirmedEventPublisher(deliveryItemRepository, deliveryClaimItemRepository, eventPublisher);

    private Delivery delivery;

    @BeforeEach
    void setUp() {
        delivery = Delivery.create(1L, 10L, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", "hash");
        ReflectionTestUtils.setField(delivery, "id", 5L);
        delivery.prepare("CJ대한통운", "658219384910");
        delivery.ship();
        delivery.deliver();
        delivery.confirm();
    }

    private DeliveryItem item(long id, long orderItemId, String name, long unitPrice, long quantity) {
        DeliveryItem item = DeliveryItem.create(delivery, orderItemId, name, unitPrice, quantity);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private ClaimedQuantity refunded(long deliveryItemId, long quantity) {
        ClaimedQuantity claimed = mock(ClaimedQuantity.class);
        when(claimed.getDeliveryItemId()).thenReturn(deliveryItemId);
        when(claimed.getQuantity()).thenReturn(quantity);
        return claimed;
    }

    private void refunds(ClaimedQuantity... refunded) {
        when(deliveryClaimItemRepository.sumQuantityByDeliveryItem(5L, ClaimType.REFUND, ClaimStatus.COMPLETED))
                .thenReturn(List.of(refunded));
    }

    private DeliveryConfirmedEvent publishedEvent() {
        ArgumentCaptor<DeliveryConfirmedEvent> captor = ArgumentCaptor.forClass(DeliveryConfirmedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("구매확정된 배송의 정보와 상품 단가·수량을 담아 이벤트를 발행한다")
    void publish() {
        when(deliveryItemRepository.findByDeliveryId(5L)).thenReturn(List.of(
                item(1L, 1000L, "티셔츠", 12_000L, 2L),
                item(2L, 1001L, "양말", 0L, 1L)));
        refunds();

        publisher.publish(delivery);

        DeliveryConfirmedEvent event = publishedEvent();
        assertThat(event.deliveryId()).isEqualTo(5L);
        assertThat(event.sellerId()).isEqualTo(10L);
        assertThat(event.orderId()).isEqualTo(100L);
        assertThat(event.purchaseType()).isEqualTo("INDIVIDUAL_GROUP");
        assertThat(event.orderedAt()).isEqualTo(delivery.getCreatedAt());
        assertThat(event.confirmedAt()).isEqualTo(delivery.getConfirmedAt());
        assertThat(event.items()).containsExactly(
                new DeliveryConfirmedEvent.Item(1000L, "티셔츠", 12_000L, 2L),
                new DeliveryConfirmedEvent.Item(1001L, "양말", 0L, 1L));
    }

    @Test
    @DisplayName("완료된 환불 수량은 상품 수량에서 뺀다")
    void subtractRefundedQuantity() {
        when(deliveryItemRepository.findByDeliveryId(5L)).thenReturn(List.of(
                item(1L, 1000L, "티셔츠", 12_000L, 3L),
                item(2L, 1001L, "양말", 5_000L, 2L)));
        refunds(refunded(1L, 1L));

        publisher.publish(delivery);

        assertThat(publishedEvent().items()).containsExactly(
                new DeliveryConfirmedEvent.Item(1000L, "티셔츠", 12_000L, 2L),
                new DeliveryConfirmedEvent.Item(1001L, "양말", 5_000L, 2L));
    }

    @Test
    @DisplayName("전량 환불된 상품은 이벤트에서 제외한다")
    void excludeFullyRefundedItem() {
        when(deliveryItemRepository.findByDeliveryId(5L)).thenReturn(List.of(
                item(1L, 1000L, "티셔츠", 12_000L, 2L),
                item(2L, 1001L, "양말", 5_000L, 2L)));
        refunds(refunded(1L, 2L));

        publisher.publish(delivery);

        assertThat(publishedEvent().items()).containsExactly(new DeliveryConfirmedEvent.Item(1001L, "양말", 5_000L, 2L));
    }

    @Test
    @DisplayName("모든 상품이 전량 환불되면 이벤트를 발행하지 않는다")
    void noEventWhenAllRefunded() {
        when(deliveryItemRepository.findByDeliveryId(5L)).thenReturn(List.of(
                item(1L, 1000L, "티셔츠", 12_000L, 2L),
                item(2L, 1001L, "양말", 5_000L, 1L)));
        refunds(refunded(1L, 2L), refunded(2L, 1L));

        publisher.publish(delivery);

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("완료된 환불 클레임만 집계한다(교환과 진행 중·거절된 클레임은 제외)")
    void onlyCompletedRefundClaims() {
        when(deliveryItemRepository.findByDeliveryId(5L)).thenReturn(List.of(item(1L, 1000L, "티셔츠", 12_000L, 2L)));
        refunds();

        publisher.publish(delivery);

        verify(deliveryClaimItemRepository).sumQuantityByDeliveryItem(5L, ClaimType.REFUND, ClaimStatus.COMPLETED);
    }
}
