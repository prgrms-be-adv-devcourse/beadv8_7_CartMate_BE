package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalServerUnavailableException;
import com.cartmate.cartmate.shared.coupon.dto.CouponOption;
import com.cartmate.cartmate.shared.coupon.dto.CouponOrderRequest;
import com.cartmate.cartmate.shared.coupon.dto.CouponOrderTarget;
import com.cartmate.cartmate.shared.coupon.dto.CouponQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class HttpOrderCouponClientTest {

    private static final String PATH = "/internal/coupons/orders";
    private static final Long MEMBER_ID = 1L;
    private static final Long MEMBER_COUPON_ID = 5L;
    private static final Long ORDER_ID = 100L;
    private static final CouponOrderTarget TARGET = new CouponOrderTarget(50_000L,
            List.of(new CouponOrderTarget.SellerAmount(7L, 50_000L)));
    private static final CouponQuote QUOTE = new CouponQuote(MEMBER_COUPON_ID, 10L, "전체 10%", 5_000L, true, null);

    @Mock
    private InternalApiClient productServerApiClient;

    private HttpOrderCouponClient couponClient;

    @BeforeEach
    void setUp() {
        couponClient = new HttpOrderCouponClient(productServerApiClient);
    }

    @Test
    @DisplayName("결제 화면 목록은 회원 · 주문 종류 ORDER · 주문 · 금액 정보를 보내고 상품 서버의 쿠폰 줄을 그대로 돌려준다")
    void options() {
        CouponOption option = new CouponOption(MEMBER_COUPON_ID, 10L, "전체 10%", "RATE", 10L, null, 0L, "ALL", null,
                LocalDateTime.of(2026, 12, 6, 0, 0), true, null, 5_000L);
        given(productServerApiClient.post(eq(PATH + "/options"),
                eq(new CouponOrderRequest(MEMBER_ID, null, "ORDER", ORDER_ID, TARGET)), any()))
                .willReturn(List.of(option));

        assertThat(couponClient.options(MEMBER_ID, ORDER_ID, TARGET)).containsExactly(option);
    }

    @Test
    @DisplayName("결제 화면 목록 응답이 비어 있으면 빈 목록으로 본다")
    void optionsNullIsEmpty() {
        given(productServerApiClient.post(eq(PATH + "/options"), any(), any())).willReturn(null);

        assertThat(couponClient.options(MEMBER_ID, ORDER_ID, TARGET)).isEmpty();
    }

    @Test
    @DisplayName("할인 계산과 사용은 회원 · 쿠폰 · 주문 · 금액 정보를 보내고 상품 서버의 할인 결과를 그대로 돌려준다")
    void quoteAndUse() {
        CouponOrderRequest request = new CouponOrderRequest(MEMBER_ID, MEMBER_COUPON_ID, "ORDER", ORDER_ID, TARGET);
        given(productServerApiClient.post(eq(PATH + "/quote"), eq(request), any())).willReturn(QUOTE);
        given(productServerApiClient.post(eq(PATH + "/use"), eq(request), any())).willReturn(QUOTE);

        assertThat(couponClient.quote(MEMBER_ID, MEMBER_COUPON_ID, ORDER_ID, TARGET)).isEqualTo(QUOTE);
        assertThat(couponClient.use(MEMBER_ID, MEMBER_COUPON_ID, ORDER_ID, TARGET)).isEqualTo(QUOTE);
    }

    @Test
    @DisplayName("상품 서버가 쿠폰을 거절하면 그 쿠폰 오류를 바꾸지 않고 그대로 던진다")
    void rejectionPropagates() {
        ServiceException expired = new ServiceException("409-COUPON_EXPIRED", "사용 기간이 지난 쿠폰입니다.");
        given(productServerApiClient.post(eq(PATH + "/use"), any(), any())).willThrow(expired);

        assertThatThrownBy(() -> couponClient.use(MEMBER_ID, MEMBER_COUPON_ID, ORDER_ID, TARGET)).isSameAs(expired);
    }

    @Test
    @DisplayName("복구는 회원 · 쿠폰 · 주문을 보내 그 주문에 쓴 쿠폰만 돌려 달라고 한다")
    void restore() {
        couponClient.restore(MEMBER_ID, MEMBER_COUPON_ID, ORDER_ID);

        then(productServerApiClient).should().post(eq(PATH + "/restore"),
                eq(new CouponOrderRequest(MEMBER_ID, MEMBER_COUPON_ID, "ORDER", ORDER_ID, null)), any());
    }

    @Test
    @DisplayName("복구에 응답이 없으면 삼키지 않고 응답 없음 예외를 던져 취소 쪽이 판단하게 한다")
    void restoreUnavailablePropagates() {
        InternalServerUnavailableException timeout = new InternalServerUnavailableException("PRODUCT", "상품",
                new SocketTimeoutException("Read timed out"));
        given(productServerApiClient.post(eq(PATH + "/restore"), any(), any())).willThrow(timeout);

        assertThatThrownBy(() -> couponClient.restore(MEMBER_ID, MEMBER_COUPON_ID, ORDER_ID)).isSameAs(timeout);
    }
}
