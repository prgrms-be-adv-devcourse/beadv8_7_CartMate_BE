package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemRepository;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DeliveryConfirmedEventPublisher {

    private final DeliveryItemRepository deliveryItemRepository;
    private final EventPublisher eventPublisher;

    // 구매확정된 배송과 그 상품 정보를 담아 DeliveryConfirmedEvent를 발행한다.
    public void publish(Delivery delivery) {
        List<DeliveryConfirmedEvent.Item> items = deliveryItemRepository.findByDeliveryId(delivery.getId()).stream()
                .map(this::toItem)
                .toList();

        eventPublisher.publish(new DeliveryConfirmedEvent(
                delivery.getId(), delivery.getSellerId(), delivery.getOrderId(), delivery.getConfirmedAt(), items
        ));
    }

    private DeliveryConfirmedEvent.Item toItem(DeliveryItem item) {
        return new DeliveryConfirmedEvent.Item(item.getOrderItemId(), item.getUnitPrice(), item.getQuantity());
    }
}
