package com.cartmate.cartmate.boundedContext.orderService.coupon.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 회원이 가진 쿠폰의 상태. 기간 만료는 저장하지 않고 만료 시각으로 판단한다
@Getter
@RequiredArgsConstructor
public enum MemberCouponStatus {
    AVAILABLE("보유"),
    RESERVED("사용 예정 (결제 전 주문에 선택)"),
    USED("사용 완료");

    private final String description;
}