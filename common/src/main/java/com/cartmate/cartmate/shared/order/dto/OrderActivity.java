package com.cartmate.cartmate.shared.order.dto;

import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.shared.order.OrderType;

import java.time.LocalDateTime;

public record OrderActivity(
        Long id,
        String orderNumber,
        OrderType orderType,
        OrderStatus status,
        long totalAmount,
        int itemCount,
        String firstProductName,
        LocalDateTime createdAt
) {
}
