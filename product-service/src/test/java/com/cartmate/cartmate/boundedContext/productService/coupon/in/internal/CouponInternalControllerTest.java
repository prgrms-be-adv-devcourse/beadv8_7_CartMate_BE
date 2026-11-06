package com.cartmate.cartmate.boundedContext.productService.coupon.in.internal;

import com.cartmate.cartmate.boundedContext.productService.code.CouponErrorCode;
import com.cartmate.cartmate.boundedContext.productService.coupon.app.CouponFacade;
import com.cartmate.cartmate.global.exception.GlobalExceptionHandler;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.coupon.dto.CouponOption;
import com.cartmate.cartmate.shared.coupon.dto.CouponOrderTarget;
import com.cartmate.cartmate.shared.coupon.dto.CouponQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CouponInternalControllerTest {

    private static final String BASE = "/internal/coupons/orders";
    private static final CouponOrderTarget TARGET = new CouponOrderTarget(50_000L, List.of(
            new CouponOrderTarget.SellerAmount(7L, 30_000L),
            new CouponOrderTarget.SellerAmount(8L, 20_000L)));
    private static final String BODY = """
            {"memberId":1,"memberCouponId":5,"orderType":"ORDER","orderId":100,
             "target":{"totalAmount":50000,"sellerAmounts":[{"sellerId":7,"amount":30000},{"sellerId":8,"amount":20000}]}}
            """;

    private final CouponFacade couponFacade = mock(CouponFacade.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CouponInternalController(couponFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("결제 화면 목록은 회원 · 주문 · 금액 정보를 넘기고 쿠폰 줄을 계약 필드 그대로 돌려준다")
    void options() throws Exception {
        given(couponFacade.optionsForOrder(1L, "ORDER", 100L, TARGET)).willReturn(List.of(new CouponOption(
                5L, 10L, "전체 10%", "RATE", 10L, null, 0L, "ALL", null,
                LocalDateTime.of(2026, 12, 6, 0, 0), true, null, 5_000L)));

        mvc.perform(post(BASE + "/options").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data[0].memberCouponId").value(5))
                .andExpect(jsonPath("$.data[0].discountType").value("RATE"))
                .andExpect(jsonPath("$.data[0].usable").value(true))
                .andExpect(jsonPath("$.data[0].expectedDiscount").value(5000))
                .andExpect(jsonPath("$.data[0].expiresAt").value("2026-12-06T00:00:00"));
    }

    @Test
    @DisplayName("할인 계산과 사용은 쿠폰 쪽 할인 결과를 그대로 돌려준다")
    void quoteAndUse() throws Exception {
        CouponQuote quote = new CouponQuote(5L, 10L, "판매자 쿠폰", 3_000L, false, 8L);
        given(couponFacade.quoteForOrder(1L, 5L, "ORDER", 100L, TARGET)).willReturn(quote);
        given(couponFacade.useForOrder(1L, 5L, "ORDER", 100L, TARGET)).willReturn(quote);

        for (String path : List.of("/quote", "/use")) {
            mvc.perform(post(BASE + path).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.memberCouponId").value(5))
                    .andExpect(jsonPath("$.data.discountAmount").value(3000))
                    .andExpect(jsonPath("$.data.platformBurden").value(false))
                    .andExpect(jsonPath("$.data.sellerId").value(8));
        }
    }

    @Test
    @DisplayName("복구는 회원 · 쿠폰 · 주문으로 쿠폰을 돌려 달라고 하고 성공으로 답한다")
    void restore() throws Exception {
        mvc.perform(post(BASE + "/restore").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"));

        then(couponFacade).should().restoreForOrder(eq(1L), eq(5L), eq("ORDER"), eq(100L));
    }

    @Test
    @DisplayName("쿠폰을 쓸 수 없으면 쿠폰 오류 코드와 문구를 그대로 응답해 주문 서버가 같은 오류를 낼 수 있다")
    void rejectionKeepsCouponErrorCode() throws Exception {
        given(couponFacade.useForOrder(1L, 5L, "ORDER", 100L, TARGET))
                .willThrow(new ServiceException(CouponErrorCode.COUPON_EXPIRED));

        mvc.perform(post(BASE + "/use").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value(CouponErrorCode.COUPON_EXPIRED.resultCode()))
                .andExpect(jsonPath("$.msg").value(CouponErrorCode.COUPON_EXPIRED.getMsg()));
    }
}
