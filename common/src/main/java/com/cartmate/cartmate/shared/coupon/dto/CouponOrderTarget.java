package com.cartmate.cartmate.shared.coupon.dto;

import java.util.List;

/**
 * 쿠폰을 쓸 주문의 금액 정보. 주문이 쿠폰에 넘긴다 (쿠폰은 주문 엔터티를 모른다).
 *
 * @param totalAmount   할인 전 주문 금액 (취소되지 않은 상품 합계)
 * @param sellerAmounts 판매자별 상품 금액 (판매자 쿠폰의 할인 대상)
 */
public record CouponOrderTarget(long totalAmount, List<SellerAmount> sellerAmounts) {

    public record SellerAmount(Long sellerId, long amount) {
    }

    public long amountOfSeller(Long sellerId) {
        return sellerAmounts.stream()
                .filter(s -> sellerId != null && sellerId.equals(s.sellerId()))
                .mapToLong(SellerAmount::amount)
                .sum();
    }
}
