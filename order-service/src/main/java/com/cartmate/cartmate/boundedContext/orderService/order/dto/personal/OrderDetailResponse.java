package com.cartmate.cartmate.boundedContext.orderService.order.dto.personal;

import io.swagger.v3.oas.annotations.media.Schema;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.shared.order.OrderCancelReason;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.OrderItem;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.shared.order.OrderType;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        Long id,
        String orderNumber,
        OrderType orderType,
        Long individualGroupPurchaseId,
        OrderStatus status,
        OrderCancelReason cancelReason,
        String cancelRejectCode,
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
                order.getCancelRejectCode(),
                order.getTotalAmount(),
                order.getPaymentDeadlineAt(),
                order.getPaidAt(),
                order.getCanceledAt(),
                order.getCreatedAt(),
                AddressResponse.from(order.getDeliveryAddress()),
                order.getItems().stream().map(ItemResponse::from).toList()
        );
    }

    @Schema(name = "OrderDetailItemResponse")
    public record ItemResponse(
            Long itemId,
            Long productId,
            Long inventoryId,
            String productName,
            String optionName,
            long unitPrice,
            int quantity,
            long amount,
            @Schema(description = "취소된 항목이면 취소 시각 (판매글 내림으로 인한 취소). 주문 전체 취소는 주문의 canceledAt을 본다")
            LocalDateTime canceledAt
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
                    item.amount(),
                    item.getCanceledAt()
            );
        }
    }

    @Schema(name = "OrderDetailAddressResponse")
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
