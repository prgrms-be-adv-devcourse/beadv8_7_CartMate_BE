package com.cartmate.cartmate.boundedContext.orderService.shared.order.event;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.boundedContext.orderService.shared.order.dto.DeliveryAddressInfo;

import java.time.LocalDateTime;
import java.util.List;

public record OrderPaidEvent(
        Long orderId,
        String orderNumber,
        Long memberId,
        String orderType,
        Long individualGroupPurchaseId,
        LocalDateTime paidAt,
        DeliveryAddressInfo deliveryAddress,
        List<Item> items
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "Order";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(orderId);
    }

    public record Item(
            Long orderItemId,
            Long productId,
            Long inventoryId,
            Long sellerId,
            String productName,
            String optionName,
            int quantity,
            long unitPrice
    ) {
    }
}
