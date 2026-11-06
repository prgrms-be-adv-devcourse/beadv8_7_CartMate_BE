package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.shared.coupon.dto.CouponOption;
import com.cartmate.cartmate.shared.coupon.dto.CouponOrderTarget;
import com.cartmate.cartmate.shared.coupon.dto.CouponQuote;

import java.util.List;

/**
 * 주문 → 쿠폰. 주문은 쿠폰 내부를 모르고 이 포트로만 부른다 (경계 테스트 R2·C2).
 * 개인 주문의 쿠폰은 주문 구분 "ORDER" + 주문 ID로 묶인다.
 */
public interface OrderCouponClient {

    String ORDER_TYPE = "ORDER";

    List<CouponOption> options(Long memberId, Long orderId, CouponOrderTarget target);

    CouponQuote quote(Long memberId, Long memberCouponId, Long orderId, CouponOrderTarget target);

    // 결제 반영과 같은 트랜잭션에서 사용 처리
    CouponQuote use(Long memberId, Long memberCouponId, Long orderId, CouponOrderTarget target);

    // 주문 전체 취소 시 복구
    void restore(Long memberId, Long memberCouponId, Long orderId);
}
