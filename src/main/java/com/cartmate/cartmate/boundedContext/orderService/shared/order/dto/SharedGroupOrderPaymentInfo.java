package com.cartmate.cartmate.boundedContext.orderService.shared.order.dto;

import java.time.LocalDateTime;

public record SharedGroupOrderPaymentInfo(
        Long sharedGroupOrderId,
        Long memberId,
        long shareAmount,
        boolean payable,
        boolean needsStockReservation,
        boolean teamAddressRequired,
        LocalDateTime paymentDeadlineAt
) {
}
