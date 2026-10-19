package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemRepository;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryConfirmedEventPublisherTest {

    private final DeliveryItemRepository deliveryItemRepository = mock(DeliveryItemRepository.class);
    private final EventPublisher eventPublisher = mock(EventPublisher.class);
    private final DeliveryConfirmedEventPublisher publisher =
            new DeliveryConfirmedEventPublisher(deliveryItemRepository, eventPublisher);

    @Test
    @DisplayName("구매확정된 배송의 정보와 상품 단가·수량을 담아 이벤트를 발행한다")
    void publish() {
        Delivery delivery = Delivery.create(1L, 10L, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", "hash");
        ReflectionTestUtils.setField(delivery, "id", 5L);
        delivery.prepare("CJ대한통운", "658219384910");
        delivery.ship();
        delivery.deliver();
        delivery.confirm();
        when(deliveryItemRepository.findByDeliveryId(5L)).thenReturn(List.of(
                DeliveryItem.create(delivery, 1000L, "티셔츠", 12_000L, 2L),
                DeliveryItem.create(delivery, 1001L, "양말", 0L, 1L)));

        publisher.publish(delivery);

        ArgumentCaptor<DeliveryConfirmedEvent> captor = ArgumentCaptor.forClass(DeliveryConfirmedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        DeliveryConfirmedEvent event = captor.getValue();
        assertThat(event.deliveryId()).isEqualTo(5L);
        assertThat(event.sellerId()).isEqualTo(10L);
        assertThat(event.orderId()).isEqualTo(100L);
        assertThat(event.confirmedAt()).isEqualTo(delivery.getConfirmedAt());
        assertThat(event.items()).containsExactly(
                new DeliveryConfirmedEvent.Item(1000L, 12_000L, 2L),
                new DeliveryConfirmedEvent.Item(1001L, 0L, 1L));
    }
}
