package com.cartmate.cartmate.boundedContext.orderService.shared.order.dto;

import java.time.LocalDateTime;

public record OrderPaymentInfo(
        Long orderId,
        Long memberId,
        String orderNumber,
        long amount,
        boolean payable,
        LocalDateTime paymentDeadlineAt
) {
}
