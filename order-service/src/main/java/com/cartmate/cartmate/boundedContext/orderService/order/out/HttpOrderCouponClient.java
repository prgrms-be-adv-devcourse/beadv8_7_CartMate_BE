package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalResponse;
import com.cartmate.cartmate.shared.coupon.dto.CouponOption;
import com.cartmate.cartmate.shared.coupon.dto.CouponOrderRequest;
import com.cartmate.cartmate.shared.coupon.dto.CouponOrderTarget;
import com.cartmate.cartmate.shared.coupon.dto.CouponQuote;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class HttpOrderCouponClient implements OrderCouponClient {

    static final String PATH = "/internal/coupons/orders";
    static final ParameterizedTypeReference<InternalResponse<List<CouponOption>>> OPTIONS = new ParameterizedTypeReference<>() {};
    static final ParameterizedTypeReference<InternalResponse<CouponQuote>> QUOTE = new ParameterizedTypeReference<>() {};
    static final ParameterizedTypeReference<InternalResponse<Void>> NONE = new ParameterizedTypeReference<>() {};

    private final InternalApiClient productServerApiClient;

    @Override
    public List<CouponOption> options(Long memberId, Long orderId, CouponOrderTarget target) {
        List<CouponOption> options = productServerApiClient.post(PATH + "/options",
                new CouponOrderRequest(memberId, null, ORDER_TYPE, orderId, target), OPTIONS);
        return options == null ? List.of() : options;
    }

    @Override
    public CouponQuote quote(Long memberId, Long memberCouponId, Long orderId, CouponOrderTarget target) {
        return productServerApiClient.post(PATH + "/quote",
                new CouponOrderRequest(memberId, memberCouponId, ORDER_TYPE, orderId, target), QUOTE);
    }

    @Override
    public CouponQuote use(Long memberId, Long memberCouponId, Long orderId, CouponOrderTarget target) {
        return productServerApiClient.post(PATH + "/use",
                new CouponOrderRequest(memberId, memberCouponId, ORDER_TYPE, orderId, target), QUOTE);
    }

    @Override
    public void restore(Long memberId, Long memberCouponId, Long orderId) {
        productServerApiClient.post(PATH + "/restore",
                new CouponOrderRequest(memberId, memberCouponId, ORDER_TYPE, orderId, null), NONE);
    }
}
