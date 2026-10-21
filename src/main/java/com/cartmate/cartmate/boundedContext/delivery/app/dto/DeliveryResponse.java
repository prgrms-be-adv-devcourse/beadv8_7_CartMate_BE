package com.cartmate.cartmate.boundedContext.delivery.app.dto;

import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;

import java.time.LocalDateTime;

public record DeliveryResponse(
        Long deliveryId,
        Long orderId,
        String orderNumber,
        PurchaseType purchaseType,
        String receiverName,
        DeliveryStatus status,
        String carrier,
        String trackingNumber,
        LocalDateTime createdAt
) {
    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getOrderNumber(),
                delivery.getPurchaseType(),
                delivery.getReceiverName(),
                delivery.getStatus(),
                delivery.getCarrier(),
                delivery.getTrackingNumber(),
                delivery.getCreatedAt()
        );
    }
}
