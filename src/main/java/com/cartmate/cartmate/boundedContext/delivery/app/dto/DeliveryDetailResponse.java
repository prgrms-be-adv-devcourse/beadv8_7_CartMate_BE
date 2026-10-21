package com.cartmate.cartmate.boundedContext.delivery.app.dto;

import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItemOption;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record DeliveryDetailResponse(
        Long deliveryId,
        Long memberId,
        Long sellerId,
        Long orderId,
        String orderNumber,
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
    public static DeliveryDetailResponse from(
            Delivery delivery,
            List<DeliveryItem> deliveryItems,
            Map<Long, List<DeliveryItemOption>> optionsByItemId
    ) {
        return new DeliveryDetailResponse(
                delivery.getId(),
                delivery.getMemberId(),
                delivery.getSellerId(),
                delivery.getOrderId(),
                delivery.getOrderNumber(),
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
                deliveryItems.stream()
                        .map(item -> Item.from(item, optionsByItemId.getOrDefault(item.getId(), List.of())))
                        .toList()
        );
    }

    @Schema(name = "DeliveryDetailItemResponse")
    public record Item(
            Long deliveryItemId,
            Long orderItemId,
            String productName,
            Long quantity,
            List<Option> options
    ) {
        public static Item from(DeliveryItem deliveryItem, List<DeliveryItemOption> options) {
            return new Item(
                    deliveryItem.getId(),
                    deliveryItem.getOrderItemId(),
                    deliveryItem.getProductName(),
                    deliveryItem.getQuantity(),
                    options.stream().map(Option::from).toList()
            );
        }
    }

    @Schema(name = "DeliveryDetailOptionResponse")
    public record Option(
            String optionName,
            String optionValue
    ) {
        public static Option from(DeliveryItemOption option) {
            return new Option(option.getOptionName(), option.getOptionValue());
        }
    }
}
