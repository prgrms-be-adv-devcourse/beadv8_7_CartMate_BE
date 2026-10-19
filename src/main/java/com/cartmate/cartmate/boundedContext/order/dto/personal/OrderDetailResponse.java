package com.cartmate.cartmate.boundedContext.order.dto.personal;

import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderCancelReason;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderItem;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderStatus;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderType;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        Long id,
        String orderNumber,
        OrderType orderType,
        Long individualGroupPurchaseId,
        OrderStatus status,
        OrderCancelReason cancelReason,
        long totalAmount,
        LocalDateTime paymentDeadlineAt,
        LocalDateTime paidAt,
        LocalDateTime canceledAt,
        LocalDateTime createdAt,
        AddressResponse deliveryAddress,
        List<ItemResponse> items
) {

    public static OrderDetailResponse from(Order order) {
        return new OrderDetailResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getOrderType(),
                order.getIndividualGroupPurchaseId(),
                order.getStatus(),
                order.getCancelReason(),
                order.getTotalAmount(),
                order.getPaymentDeadlineAt(),
                order.getPaidAt(),
                order.getCanceledAt(),
                order.getCreatedAt(),
                AddressResponse.from(order.getDeliveryAddress()),
                order.getItems().stream().map(ItemResponse::from).toList()
        );
    }

    public record ItemResponse(
            Long itemId,
            Long productId,
            Long inventoryId,
            String productName,
            String optionName,
            long unitPrice,
            int quantity,
            long amount
    ) {

        static ItemResponse from(OrderItem item) {
            return new ItemResponse(
                    item.getId(),
                    item.getProductId(),
                    item.getInventoryId(),
                    item.getProductName(),
                    item.getOptionName(),
                    item.getUnitPrice(),
                    item.getQuantity(),
                    item.amount()
            );
        }
    }

    public record AddressResponse(
            String receiverName,
            String receiverPhone,
            String zipCode,
            String address,
            String addressDetail
    ) {

        static AddressResponse from(DeliveryAddress address) {
            return new AddressResponse(
                    address.getReceiverName(),
                    address.getReceiverPhone(),
                    address.getZipCode(),
                    address.getAddress(),
                    address.getAddressDetail()
            );
        }
    }
}
