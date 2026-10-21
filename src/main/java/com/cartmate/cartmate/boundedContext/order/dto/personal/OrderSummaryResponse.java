package com.cartmate.cartmate.boundedContext.order.dto.personal;

import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderItem;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderStatus;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderType;

import java.time.LocalDateTime;

public record OrderSummaryResponse(
        Long id,
        String orderNumber,
        OrderType orderType,
        OrderStatus status,
        long totalAmount,
        int itemCount,
        String firstProductName,
        LocalDateTime paymentDeadlineAt,
        LocalDateTime createdAt
) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getOrderType(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getItems().size(),
                order.getItems().stream().findFirst().map(OrderItem::getProductName).orElse(null),
                order.getPaymentDeadlineAt(),
                order.getCreatedAt()
        );
    }
}
