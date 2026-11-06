package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.boundedContext.orderService.coupon.app.CouponFacade;
import com.cartmate.cartmate.shared.coupon.dto.CouponOption;
import com.cartmate.cartmate.shared.coupon.dto.CouponOrderTarget;
import com.cartmate.cartmate.shared.coupon.dto.CouponQuote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

// 같은 주문 서비스 안의 쿠폰 모듈을 Facade로 부른다. 쿠폰을 다른 서버로 나누면 이 어댑터만 HTTP로 바꾼다
@Component
@RequiredArgsConstructor
public class CouponFacadeOrderCouponClient implements OrderCouponClient {

    private final CouponFacade couponFacade;

    @Override
    public List<CouponOption> options(Long memberId, Long orderId, CouponOrderTarget target) {
        return couponFacade.optionsForOrder(memberId, ORDER_TYPE, orderId, target);
    }

    @Override
    public CouponQuote quote(Long memberId, Long memberCouponId, Long orderId, CouponOrderTarget target) {
        return couponFacade.quoteForOrder(memberId, memberCouponId, ORDER_TYPE, orderId, target);
    }

    @Override
    public CouponQuote use(Long memberId, Long memberCouponId, Long orderId, CouponOrderTarget target) {
        return couponFacade.useForOrder(memberId, memberCouponId, ORDER_TYPE, orderId, target);
    }

    @Override
    public void restore(Long memberId, Long memberCouponId, Long orderId) {
        couponFacade.restoreForOrder(memberId, memberCouponId, ORDER_TYPE, orderId);
    }
}
