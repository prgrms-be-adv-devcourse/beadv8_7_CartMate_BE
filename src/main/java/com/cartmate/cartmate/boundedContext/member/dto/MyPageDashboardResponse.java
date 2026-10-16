package com.cartmate.cartmate.boundedContext.member.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MyPageDashboardResponse(
        Wallet wallet,
        List<GroupPurchase> groupPurchases,
        List<RecentOrder> recentOrders,
        long shippingCount
) {
    public record Wallet(long balance, long heldBalance) {
    }

    public record GroupPurchase(
            Long id,
            String purchaseType,
            String title,
            int itemCount,
            long myAmount,
            int joinedCount,
            int targetCount,
            LocalDateTime editDeadlineAt
    ) {
    }

    public record RecentOrder(
            Long id,
            String orderNumber,
            LocalDateTime orderedAt,
            String title,
            int itemCount,
            int totalQuantity,
            long totalAmount,
            String orderStatus,
            String deliveryStatus
    ) {
    }
}