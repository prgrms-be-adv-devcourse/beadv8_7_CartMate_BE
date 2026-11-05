package com.cartmate.cartmate.shared.order.dto;

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
