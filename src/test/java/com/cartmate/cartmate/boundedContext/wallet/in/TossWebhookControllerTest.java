package com.cartmate.cartmate.boundedContext.wallet.in;

import com.cartmate.cartmate.boundedContext.wallet.app.WalletChargeWebhookService;
import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayException;
import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayException.FailureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 웹훅 본문 해석과 응답 코드만 검증한다. 200이 아니면 토스가 웹훅을 다시 보낸다.
class TossWebhookControllerTest {

    private static final String URL = "/api/wallets/webhooks/toss";

    private final WalletChargeWebhookService service = mock(WalletChargeWebhookService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TossWebhookController(service)).build();
    }

    private org.springframework.test.web.servlet.ResultActions send(String body) throws Exception {
        return mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("결제 상태 변경 웹훅은 결제키로 충전 주문을 동기화하고 200을 응답한다")
    void paymentStatusChanged() throws Exception {
        send("""
                {"eventType":"PAYMENT_STATUS_CHANGED","createdAt":"2026-10-20T10:00:00",
                 "data":{"paymentKey":"pk_1","orderId":"Charge_1","status":"DONE"}}
                """).andExpect(status().isOk());

        verify(service).sync("pk_1");
    }

    @Test
    @DisplayName("처리하지 않는 이벤트는 200으로 받고 아무것도 하지 않는다")
    void ignoredEvent() throws Exception {
        send("""
                {"eventType":"PAYOUT_STATUS_CHANGED","data":{"id":"p1"}}
                """).andExpect(status().isOk());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("결제키가 없으면 200으로 받고 아무것도 하지 않는다")
    void missingPaymentKey() throws Exception {
        send("""
                {"eventType":"PAYMENT_STATUS_CHANGED","data":{}}
                """).andExpect(status().isOk());

        verify(service, never()).sync(anyString());
    }

    @Test
    @DisplayName("결제사 조회가 실패하면 예외가 전파되어 토스가 웹훅을 다시 보낸다")
    void gatewayFailurePropagates() {
        doThrow(new ChargeGatewayException(FailureType.UNKNOWN, null, "오류")).when(service).sync("pk_1");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> send("""
                {"eventType":"PAYMENT_STATUS_CHANGED","data":{"paymentKey":"pk_1"}}
                """)).hasCauseInstanceOf(ChargeGatewayException.class);
    }
}
