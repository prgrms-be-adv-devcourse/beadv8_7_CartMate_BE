package com.cartmate.cartmate.boundedContext.order.dto.personal;

import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;

import java.time.LocalDateTime;

public record OrderCreateResponse(
        Long orderId,
        String orderNumber,
        long totalAmount,
        LocalDateTime paymentDeadlineAt
) {

    public static OrderCreateResponse from(Order order) {
        return new OrderCreateResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getTotalAmount(),
                order.getPaymentDeadlineAt()
        );
    }
}
