package com.cartmate.cartmate.boundedContext.payout.in;

import com.cartmate.cartmate.boundedContext.payout.app.PayoutFacade;
import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementDetailResponse;
import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementInfoResponse;
import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementResponse;
import com.cartmate.cartmate.boundedContext.payout.domain.Payout;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.payout.domain.SettlementKey;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
                .thenReturn(List.of(new SettlementResponse("20261115-COMPLETED",
                        LocalDate.of(2026, 11, 15), PayoutStatus.COMPLETED,
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 18, 180_000L, 5_400L, 174_600L)));

        mvc.perform(get("/api/settlements").param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data[0].settlementId").value("20261115-COMPLETED"))
                .andExpect(jsonPath("$.data[0].settlementDate").value("2026-11-15"))
                .andExpect(jsonPath("$.data[0].periodStart").value("2026-10-01"))
                .andExpect(jsonPath("$.data[0].periodEnd").value("2026-10-31"))
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

    @Test
    @DisplayName("내 정산 정보는 RsData로 감싸서 내려가고 로그인한 회원 ID로 조회한다")
    void mySettlementInfo() throws Exception {
        when(payoutFacade.getMySettlementInfo(20L)).thenReturn(new SettlementInfoResponse(
                3, 15, LocalDate.of(2026, 11, 15), 3L, 101_850L, 97_000L));

        mvc.perform(get("/api/settlements/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.feeRatePercent").value(3))
                .andExpect(jsonPath("$.data.payoutDayOfMonth").value(15))
                .andExpect(jsonPath("$.data.nextPayoutDate").value("2026-11-15"))
                .andExpect(jsonPath("$.data.pendingPayoutCount").value(3))
                .andExpect(jsonPath("$.data.pendingPayoutAmount").value(101850))
                .andExpect(jsonPath("$.data.completedPayoutAmount").value(97000));
        verify(payoutFacade).getMySettlementInfo(20L);
    }

    @Test
    @DisplayName("정산 상세는 정산 ID로 조회하고 RsData로 감싸서 내려간다")
    void settlementDetail() throws Exception {
        Payout payout = Payout.create(10L, 100L, 1000L, "티셔츠", 2L, 24_000L,
                LocalDateTime.of(2026, 10, 23, 9, 0), LocalDateTime.of(2026, 10, 25, 10, 0));
        when(payoutFacade.getMySettlementDetail(20L, "20261115-PENDING")).thenReturn(
                SettlementDetailResponse.of(new SettlementKey(LocalDate.of(2026, 11, 15), PayoutStatus.PENDING), List.of(payout)));

        mvc.perform(get("/api/settlements/20261115-PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.settlementId").value("20261115-PENDING"))
                .andExpect(jsonPath("$.data.periodStart").value("2026-10-01"))
                .andExpect(jsonPath("$.data.periodEnd").value("2026-10-31"))
                .andExpect(jsonPath("$.data.payoutCount").value(1))
                .andExpect(jsonPath("$.data.totalQuantity").value(2))
                .andExpect(jsonPath("$.data.salesAmount").value(24000))
                .andExpect(jsonPath("$.data.feeAmount").value(720))
                .andExpect(jsonPath("$.data.payoutAmount").value(23280))
                .andExpect(jsonPath("$.data.items[0].productName").value("티셔츠"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].orderedAt").value("2026-10-23T09:00:00"));
        verify(payoutFacade).getMySettlementDetail(20L, "20261115-PENDING");
    }

    @Test
    @DisplayName("정산 ID 형식이 틀리면 400, 해당 정산이 없으면 404 오류로 변환된다")
    void settlementDetailErrors() throws Exception {
        when(payoutFacade.getMySettlementDetail(20L, "abc"))
                .thenThrow(new ServiceException(PayoutErrorCode.PAYOUT_INVALID_SETTLEMENT_ID));
        when(payoutFacade.getMySettlementDetail(20L, "20261115-PENDING"))
                .thenThrow(new ServiceException(PayoutErrorCode.PAYOUT_SETTLEMENT_NOT_FOUND));

        mvc.perform(get("/api/settlements/abc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/settlements/20261115-PENDING"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/info는 정산 ID가 아니라 정산 정보 조회로 연결된다")
    void infoIsNotSettlementId() throws Exception {
        when(payoutFacade.getMySettlementInfo(20L)).thenReturn(new SettlementInfoResponse(
                3, 15, LocalDate.of(2026, 11, 15), 0L, 0L, 0L));

        mvc.perform(get("/api/settlements/info")).andExpect(status().isOk());
        verify(payoutFacade, never()).getMySettlementDetail(any(), any());
    }

    @Test
    @DisplayName("판매자가 아니면 정산 정보 조회도 403 오류로 변환된다")
    void settlementInfoSellerOnly() throws Exception {
        when(payoutFacade.getMySettlementInfo(20L))
                .thenThrow(new ServiceException(PayoutErrorCode.PAYOUT_SELLER_ONLY));

        mvc.perform(get("/api/settlements/info"))
                .andExpect(status().isForbidden());
    }
}
