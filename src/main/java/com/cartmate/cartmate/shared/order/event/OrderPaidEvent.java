package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.shared.order.dto.DeliveryAddressInfo;

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
) {

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
