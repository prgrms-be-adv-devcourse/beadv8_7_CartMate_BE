package com.cartmate.cartmate.boundedContext.delivery.app.dto;

import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;

import java.time.LocalDateTime;
import java.util.List;

public record DeliveryDetailResponse(
        Long deliveryId,
        Long memberId,
        Long sellerId,
        Long orderId,
        PurchaseType purchaseType,
        DeliveryStatus status,
        String carrier,
        String trackingNumber,
        String receiverName,
        String receiverPhone,
        String zipCode,
        String address,
        String addressDetail,
        LocalDateTime createdAt,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        LocalDateTime canceledAt,
        LocalDateTime confirmedAt,
        List<Item> items
) {
    public static DeliveryDetailResponse from(Delivery delivery, List<DeliveryItem> deliveryItems) {
        return new DeliveryDetailResponse(
                delivery.getId(),
                delivery.getMemberId(),
                delivery.getSellerId(),
                delivery.getOrderId(),
                delivery.getPurchaseType(),
                delivery.getStatus(),
                delivery.getCarrier(),
                delivery.getTrackingNumber(),
                delivery.getReceiverName(),
                delivery.getReceiverPhone(),
                delivery.getZipCode(),
                delivery.getAddress(),
                delivery.getAddressDetail(),
                delivery.getCreatedAt(),
                delivery.getShippedAt(),
                delivery.getDeliveredAt(),
                delivery.getCanceledAt(),
                delivery.getConfirmedAt(),
                deliveryItems.stream().map(Item::from).toList()
        );
    }

    public record Item(
            Long orderItemId,
            Long quantity
    ) {
        public static Item from(DeliveryItem deliveryItem) {
            return new Item(deliveryItem.getOrderItemId(), deliveryItem.getQuantity());
        }
    }
}
