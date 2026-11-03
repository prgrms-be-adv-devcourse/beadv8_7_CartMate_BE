package com.cartmate.cartmate.boundedContext.payment.domain;

/**
 * 환불 중복 방지 키를 만드는 곳.
 *
 * <p>같은 원인으로 같은 결제를 두 번 환불하지 않도록, 환불마다 "원인 + 대상"으로 키를 정하고 DB에 UNIQUE로 저장한다.
 * 키 형식을 여기 한곳에서만 정해서, 문자열이 여러 곳에 흩어지거나 형식이 어긋나지 않게 한다.
 */
public final class RefundKey {

    private RefundKey() {
    }

    // 판매자가 승인한 취소·반품 요청
    public static String claim(Long claimId) {
        return "CLAIM-" + claimId;
    }

    // 결제 후 구매자 주문 취소 (전액)
    public static String orderCancel(Long paymentId) {
        return "ORDER-CANCEL-" + paymentId;
    }

    // 공동 주문 무산·취소 (결제한 사람마다 전액)
    public static String sharedGroupCancel(Long paymentId) {
        return "SHARED-CANCEL-" + paymentId;
    }

    // 판매 중지: 개인 주문에서 한 판매자 항목만 취소 (주문·판매자당 한 번)
    public static String orderSellerItems(Long orderId, Long sellerId) {
        return "SALES-STOP-O%d-S%d".formatted(orderId, sellerId);
    }

    // 판매 중지: 공동 주문에서 한 판매자 항목만 취소 (결제한 사람마다 나눠서)
    public static String sharedSellerItems(Long sharedGroupOrderId, Long sellerId, Long paymentId) {
        return "SALES-STOP-SG%d-S%d-PAY%d".formatted(sharedGroupOrderId, sellerId, paymentId);
    }

    // 관리자 수동 환불: 같은 결제를 여러 번 나눠 환불할 수 있어 시각을 붙인다
    public static String admin(Long paymentId, long requestedAtMillis) {
        return "ADMIN-%d-%d".formatted(paymentId, requestedAtMillis);
    }
}