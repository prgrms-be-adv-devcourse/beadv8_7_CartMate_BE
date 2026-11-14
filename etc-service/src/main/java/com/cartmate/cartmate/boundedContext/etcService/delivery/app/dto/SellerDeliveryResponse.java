package com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto;

import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryItemOption;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import com.cartmate.cartmate.shared.delivery.PurchaseType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// 판매자 배송 관리 목록의 한 행. 주문번호/일시, 상품·옵션·수량, 수령인·주소, 배송상태, 송장 정보, 취소 시각·사유를 담는다.
public record SellerDeliveryResponse(
        Long deliveryId,
        Long orderId,
        String orderNumber,
        PurchaseType purchaseType,
        DeliveryStatus status,
        LocalDateTime createdAt,
        String receiverName,
        String receiverPhone,
        String zipCode,
        String address,
        String addressDetail,
        String carrier,
        String trackingNumber,
        LocalDateTime canceledAt,
        DeliveryCancelReason cancelReason,
        List<DeliveryDetailResponse.Item> items
) {
    public static SellerDeliveryResponse from(
            Delivery delivery,
            List<DeliveryItem> deliveryItems,
            Map<Long, List<DeliveryItemOption>> optionsByItemId
    ) {
        return new SellerDeliveryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getOrderNumber(),
                delivery.getPurchaseType(),
                delivery.getStatus(),
                delivery.getCreatedAt(),
                delivery.getReceiverName(),
                delivery.getReceiverPhone(),
                delivery.getZipCode(),
                delivery.getAddress(),
                delivery.getAddressDetail(),
                delivery.getCarrier(),
                delivery.getTrackingNumber(),
                delivery.getCanceledAt(),
                delivery.getCancelReason(),
                deliveryItems.stream()
                        .map(item -> DeliveryDetailResponse.Item.from(
                                item, optionsByItemId.getOrDefault(item.getId(), List.of())))
                        .toList()
        );
    }
}
