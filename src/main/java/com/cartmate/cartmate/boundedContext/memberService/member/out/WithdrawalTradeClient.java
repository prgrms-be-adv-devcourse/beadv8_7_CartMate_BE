package com.cartmate.cartmate.boundedContext.memberService.member.out;

/**
 * 회원 탈퇴 가능 여부를 판단하려고 다른 모듈의 진행 중 거래를 묻는다.
 * 거래가 끝났다 = 구매확정 또는 취소·환불 완료. 그 전은 모두 진행 중이다.
 */
public interface WithdrawalTradeClient {

    // 구매자로서 진행 중인 거래
    BuyerTrades getBuyerTrades(Long memberId);

    // 판매자로서 진행 중인 거래
    SellerTrades getSellerTrades(Long sellerId);

    record BuyerTrades(
            long paymentPendingOrders,      // 결제 대기 주문
            long unconfirmedDeliveries,     // 구매확정 전 주문 (개인·공동)
            long payingSharedOrders,        // 분담금 결제 중인 공동 주문
            long claimsInProgress,          // 처리 중인 교환·반품
            long activeGroupPurchases       // 모집 중인 공동구매 (참여·개설)
    ) {}

    record SellerTrades(
            long unconfirmedDeliveries,     // 발송·구매확정 전 판매 건
            long claimsInProgress,          // 처리 중인 교환·반품
            long pendingPayouts             // 지급 전 정산
    ) {}
}