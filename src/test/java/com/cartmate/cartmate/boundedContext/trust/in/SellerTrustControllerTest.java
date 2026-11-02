package com.cartmate.cartmate.boundedContext.trust.in;

import com.cartmate.cartmate.boundedContext.trust.app.TrustFacade;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustLevel;
import com.cartmate.cartmate.boundedContext.trust.dto.SellerTrustResponse;
import com.cartmate.cartmate.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 컨트롤러 매핑·응답 형식만 검증한다. 인증 필터와 DB는 거치지 않는다.
class SellerTrustControllerTest {

    private final TrustFacade trustFacade = mock(TrustFacade.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SellerTrustController(trustFacade))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("판매자 신뢰도를 RsData로 응답한다")
    void getSellerTrust() throws Exception {
        when(trustFacade.getSellerTrust(1L)).thenReturn(new SellerTrustResponse(1L, 90, TrustLevel.HIGH));

        mvc.perform(get("/api/sellers/1/trust"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.sellerId").value(1))
                .andExpect(jsonPath("$.data.score").value(90))
                .andExpect(jsonPath("$.data.level").value("HIGH"));
    }

    @Test
    @DisplayName("점수가 없는 NEW 판매자는 score가 null이다")
    void unratedSeller() throws Exception {
        when(trustFacade.getSellerTrust(2L)).thenReturn(SellerTrustResponse.unrated(2L));

        mvc.perform(get("/api/sellers/2/trust"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(nullValue()))
                .andExpect(jsonPath("$.data.level").value("NEW"));
    }

    @Test
    @DisplayName("공개 응답에는 거래 건수를 포함하지 않는다")
    void doesNotExposeCounts() throws Exception {
        when(trustFacade.getSellerTrust(1L)).thenReturn(new SellerTrustResponse(1L, 90, TrustLevel.HIGH));

        mvc.perform(get("/api/sellers/1/trust"))
                .andExpect(jsonPath("$.data.confirmedCount").doesNotExist())
                .andExpect(jsonPath("$.data.faultClaimCount").doesNotExist());
    }
}
