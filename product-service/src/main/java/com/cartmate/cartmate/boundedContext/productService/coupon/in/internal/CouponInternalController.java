package com.cartmate.cartmate.boundedContext.productService.coupon.in.internal;

import com.cartmate.cartmate.boundedContext.productService.coupon.app.CouponFacade;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.coupon.dto.CouponOption;
import com.cartmate.cartmate.shared.coupon.dto.CouponOrderRequest;
import com.cartmate.cartmate.shared.coupon.dto.CouponQuote;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Hidden
@RestController
@RequestMapping("/internal/coupons/orders")
@RequiredArgsConstructor
public class CouponInternalController {

    private final CouponFacade couponFacade;

    @PostMapping("/options")
    public RsData<List<CouponOption>> options(@RequestBody CouponOrderRequest request) {
        return new RsData<>("200-1", "주문 쿠폰 목록", couponFacade.optionsForOrder(
                request.memberId(), request.orderType(), request.orderId(), request.target()));
    }

    @PostMapping("/quote")
    public RsData<CouponQuote> quote(@RequestBody CouponOrderRequest request) {
        return new RsData<>("200-1", "주문 쿠폰 할인 계산", couponFacade.quoteForOrder(
                request.memberId(), request.memberCouponId(), request.orderType(), request.orderId(), request.target()));
    }

    @PostMapping("/use")
    public RsData<CouponQuote> use(@RequestBody CouponOrderRequest request) {
        return new RsData<>("200-1", "주문 쿠폰 사용", couponFacade.useForOrder(
                request.memberId(), request.memberCouponId(), request.orderType(), request.orderId(), request.target()));
    }

    @PostMapping("/restore")
    public RsData<Void> restore(@RequestBody CouponOrderRequest request) {
        couponFacade.restoreForOrder(request.memberId(), request.memberCouponId(), request.orderType(), request.orderId());
        return new RsData<>("200-1", "주문 쿠폰 복구");
    }
}
