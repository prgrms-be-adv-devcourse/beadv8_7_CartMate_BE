package com.cartmate.cartmate.boundedContext.wallet.in;

import com.cartmate.cartmate.boundedContext.wallet.app.WalletChargeConfirmService;
import com.cartmate.cartmate.boundedContext.wallet.app.WalletFacade;
import com.cartmate.cartmate.boundedContext.wallet.app.WalletRetryService;
import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletBalanceResponse;
import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletTransactionSummaryResponse;
import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.global.exception.GlobalExceptionHandler;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 컨트롤러 매핑, 요청 검증, 응답 형식, 예외 변환만 검증한다. 인증 필터와 DB는 거치지 않는다.
class WalletControllerTest {

    private static final Long MEMBER_ID = 20L;
    private static final Long WALLET_ID = 7L;
    private static final String CONFIRM_URL = "/api/wallets/me/charges/confirm";

    private final WalletFacade walletFacade = mock(WalletFacade.class);
    private final WalletRetryService walletRetryService = mock(WalletRetryService.class);
    private final WalletChargeConfirmService walletChargeConfirmService = mock(WalletChargeConfirmService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new WalletController(walletFacade, walletRetryService, walletChargeConfirmService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SecurityUser(MEMBER_ID, "buyer@a.com", "BUYER"), null, List.of()));
        when(walletFacade.getWallet(MEMBER_ID)).thenReturn(new WalletBalanceResponse(WALLET_ID, 10_000L, 3_000L));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("거래 내역 요약 조회는 내 지갑의 충전·결제 합계를 반환한다")
    void getMyWalletTransactionSummary() throws Exception {
        when(walletFacade.getTransactionSummary(WALLET_ID)).thenReturn(new WalletTransactionSummaryResponse(200_000L, 93_000L));

        mvc.perform(get("/api/wallets/me/transactions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.totalCharged").value(200000))
                .andExpect(jsonPath("$.data.totalPaid").value(93000));
    }

    private static String body(String orderNumber, String paymentKey, Object amount) {
        return """
                {"orderNumber":%s,"paymentKey":%s,"amount":%s}
                """.formatted(orderNumber == null ? "null" : "\"" + orderNumber + "\"",
                paymentKey == null ? "null" : "\"" + paymentKey + "\"", amount);
    }

    @Test
    @DisplayName("충전 승인은 로그인한 회원의 지갑으로 승인 서비스를 호출하고 충전된 잔액을 돌려준다")
    void confirm() throws Exception {
        mvc.perform(post(CONFIRM_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Charge_1", "pk_1", 10_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.walletId").value(7))
                .andExpect(jsonPath("$.data.balance").value(10000))
                .andExpect(jsonPath("$.data.heldBalance").value(3000));

        verify(walletChargeConfirmService).confirm(WALLET_ID, "Charge_1", "pk_1", 10_000L);
    }

    @Test
    @DisplayName("충전 승인은 승인 서비스를 거치지 않고 곧바로 충전하지 않는다")
    void confirmNeverBypassesApproval() throws Exception {
        mvc.perform(post(CONFIRM_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Charge_1", "pk_1", 10_000)))
                .andExpect(status().isOk());

        verify(walletRetryService, never()).charge(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("결제가 거절되면 400 오류를 돌려준다")
    void confirmRejected() throws Exception {
        doThrow(new ServiceException(WalletErrorCode.WALLET_CHARGE_PAYMENT_REJECTED))
                .when(walletChargeConfirmService).confirm(WALLET_ID, "Charge_1", "pk_1", 10_000L);

        mvc.perform(post(CONFIRM_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Charge_1", "pk_1", 10_000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-WALLET_CHARGE_PAYMENT_REJECTED"));
    }

    @Test
    @DisplayName("승인 결과를 확인 중이면 409 오류를 돌려준다")
    void confirmProcessing() throws Exception {
        doThrow(new ServiceException(WalletErrorCode.WALLET_CHARGE_PAYMENT_PROCESSING))
                .when(walletChargeConfirmService).confirm(WALLET_ID, "Charge_1", "pk_1", 10_000L);

        mvc.perform(post(CONFIRM_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Charge_1", "pk_1", 10_000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-WALLET_CHARGE_PAYMENT_PROCESSING"));
    }

    @Test
    @DisplayName("주문번호, 결제 키, 금액이 없거나 올바르지 않으면 400 오류이고 승인 서비스를 호출하지 않는다")
    void confirmInvalidInput() throws Exception {
        for (String invalid : List.of(
                body(null, "pk_1", 10_000),
                body("Charge_1", null, 10_000),
                body("Charge_1", "", 10_000),
                body("Charge_1", "pk_1", "null"),
                body("Charge_1", "pk_1", 0),
                body("Charge_1", "pk_1", -1))) {
            mvc.perform(post(CONFIRM_URL).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(walletChargeConfirmService);
    }
}
