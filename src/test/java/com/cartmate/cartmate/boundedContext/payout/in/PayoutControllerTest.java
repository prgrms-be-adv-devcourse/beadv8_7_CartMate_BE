package com.cartmate.cartmate.boundedContext.payout.in;

import com.cartmate.cartmate.boundedContext.payout.app.PayoutFacade;
import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementResponse;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.payout.exception.PayoutErrorCode;
import com.cartmate.cartmate.global.exception.GlobalExceptionHandler;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 컨트롤러 매핑, 파라미터 변환, 응답 형식, 예외 변환만 검증한다. 인증 필터와 DB는 거치지 않는다.
class PayoutControllerTest {

    private final PayoutFacade payoutFacade = mock(PayoutFacade.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PayoutController(payoutFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SecurityUser(20L, "seller@a.com", "SELLER"), null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("내 정산 내역은 RsData로 감싸서 내려가고 날짜 파라미터를 변환해 로그인한 회원 ID로 조회한다")
    void mySettlements() throws Exception {
        when(payoutFacade.getMySettlements(20L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
                .thenReturn(List.of(new SettlementResponse(
                        LocalDate.of(2026, 11, 15), PayoutStatus.COMPLETED, 18, 180_000L, 5_400L, 174_600L)));

        mvc.perform(get("/api/settlements").param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data[0].settlementDate").value("2026-11-15"))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].payoutCount").value(18))
                .andExpect(jsonPath("$.data[0].salesAmount").value(180000))
                .andExpect(jsonPath("$.data[0].feeAmount").value(5400))
                .andExpect(jsonPath("$.data[0].payoutAmount").value(174600));
        verify(payoutFacade).getMySettlements(20L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("날짜 파라미터를 생략해도 조회된다")
    void withoutDates() throws Exception {
        when(payoutFacade.getMySettlements(20L, null, null)).thenReturn(List.of());

        mvc.perform(get("/api/settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(payoutFacade).getMySettlements(20L, null, null);
    }

    @Test
    @DisplayName("날짜 형식이 잘못되면 400 오류다")
    void invalidDateFormat() throws Exception {
        mvc.perform(get("/api/settlements").param("from", "2026/11/01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("판매자가 아니면 403 오류로 변환된다")
    void sellerOnly() throws Exception {
        when(payoutFacade.getMySettlements(20L, null, null))
                .thenThrow(new ServiceException(PayoutErrorCode.PAYOUT_SELLER_ONLY));

        mvc.perform(get("/api/settlements"))
                .andExpect(status().isForbidden());
    }
}
