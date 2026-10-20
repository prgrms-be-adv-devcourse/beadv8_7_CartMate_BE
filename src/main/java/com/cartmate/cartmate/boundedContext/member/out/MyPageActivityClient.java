package com.cartmate.cartmate.boundedContext.member.out;

import java.time.LocalDateTime;
import java.util.List;

// 마이페이지에 필요한 다른 모듈의 회원별 활동 정보
public interface MyPageActivityClient {

    long getWalletBalance(Long memberId);

    List<SharedPurchase> getSharedPurchases(Long memberId);

    List<SharedOrder> getSharedOrders(Long memberId);

    List<IndividualPurchase> getIndividualPurchases(Long memberId);

    List<Order> getOrders(Long memberId);

    List<Delivery> getDeliveries(Long memberId);

    record SharedPurchase(Long id, String title, String status, int participantCount, int maxParticipants,
                          LocalDateTime editDeadlineAt) {}

    record SharedOrder(Long id, Long sharedGroupPurchaseId, String status, long myShareAmount,
                       String myPaymentStatus, int paidCount, int participantCount, LocalDateTime paymentDeadlineAt) {}

    record IndividualPurchase(Long id, String title, boolean open, int productCount, LocalDateTime deadlineAt) {}

    record Order(Long id, String orderNumber, String orderType, String status, long totalAmount,
                 int itemCount, String firstProductName, LocalDateTime createdAt) {}

    record Delivery(Long orderId, String purchaseType, String status, LocalDateTime createdAt) {}
}