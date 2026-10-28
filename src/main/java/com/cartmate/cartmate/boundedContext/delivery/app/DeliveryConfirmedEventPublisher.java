package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryItemRepository;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DeliveryConfirmedEventPublisher {

    private final DeliveryItemRepository deliveryItemRepository;
    private final DeliveryClaimItemRepository deliveryClaimItemRepository;
    private final EventPublisher eventPublisher;

    // 구매확정된 배송과 그 상품 정보를 담아 DeliveryConfirmedEvent를 발행한다.
    // 구매확정 전에 환불이 완료된 수량은 판매가 아니므로 상품 수량에서 빼고, 전량 환불된 상품은 제외한다.
    // 남는 상품이 하나도 없으면 정산할 것이 없으므로 이벤트를 발행하지 않는다.
    public void publish(Delivery delivery) {
        Map<Long, Long> refundedQuantities = refundedQuantities(delivery.getId());

        List<DeliveryConfirmedEvent.Item> items = deliveryItemRepository.findByDeliveryId(delivery.getId()).stream()
                .map(item -> toItem(item, refundedQuantities.getOrDefault(item.getId(), 0L)))
                .filter(item -> item.quantity() > 0)
                .toList();

        if (items.isEmpty()) {
            return;
        }

        eventPublisher.publish(new DeliveryConfirmedEvent(
                delivery.getId(), delivery.getSellerId(), delivery.getOrderId(), delivery.getPurchaseType(),
                delivery.getCreatedAt(), delivery.getConfirmedAt(), items
        ));
    }

    // 완료된 환불 클레임의 수량을 배송상품별로 모은다. 교환은 판매가 유지되므로 포함하지 않는다.
    private Map<Long, Long> refundedQuantities(Long deliveryId) {
        return deliveryClaimItemRepository
                .sumQuantityByDeliveryItem(deliveryId, ClaimType.REFUND, ClaimStatus.COMPLETED).stream()
                .collect(Collectors.toMap(
                        DeliveryClaimItemRepository.ClaimedQuantity::getDeliveryItemId,
                        DeliveryClaimItemRepository.ClaimedQuantity::getQuantity));
    }

    // 배송 수량에서 환불된 수량을 뺀 값으로 이벤트 상품을 만든다.
    private DeliveryConfirmedEvent.Item toItem(DeliveryItem item, long refundedQuantity) {
        return new DeliveryConfirmedEvent.Item(
                item.getOrderItemId(), item.getProductName(), item.getUnitPrice(), item.getQuantity() - refundedQuantity);
    }
}
