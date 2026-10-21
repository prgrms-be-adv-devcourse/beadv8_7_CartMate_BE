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

    // 진행 중인 공동구매. 모집 중이거나, 공동물품 결제가 진행 중인 것
    public record GroupPurchase(
            Long id,
            String purchaseType,       // SHARED(공동물품), PER_PERSON(개인물품)
            String stage,              // RECRUITING(모집 중), PAYING(분담금 결제 중)
            String title,
            int itemCount,
            long myAmount,             // 결제 중일 때 내 분담금, 모집 중이면 0
            int joinedCount,           // 모집 중: 참여 인원 / 결제 중: 결제한 인원
            int targetCount,           // 모집 중: 최대 인원 / 결제 중: 전체 참여 인원
            String myPaymentStatus,    // 결제 중일 때 UNPAID, PAID
            LocalDateTime deadlineAt,  // 모집 중: 수정 마감 / 결제 중: 결제 기한
            Long sharedGroupOrderId    // 결제 중이면 결제 화면 이동용
    ) {
    }

    public record RecentOrder(
            Long id,
            String orderNumber,
            String orderType,          // PERSONAL, INDIVIDUAL_GROUP
            LocalDateTime orderedAt,
            String title,
            int itemCount,
            long totalAmount,
            String orderStatus,        // PAYMENT_PENDING, PAID, CANCELED
            String deliveryStatus      // REQUESTED, PREPARING, SHIPPING, DELIVERED, CONFIRMED, CANCELED (없으면 null)
    ) {
    }
}