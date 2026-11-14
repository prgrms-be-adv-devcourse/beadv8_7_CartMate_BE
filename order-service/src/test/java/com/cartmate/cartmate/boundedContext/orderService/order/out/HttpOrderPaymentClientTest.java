package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalServerUnavailableException;
import com.cartmate.cartmate.shared.payment.dto.PaymentChargeRequest;
import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class HttpOrderPaymentClientTest {

    private static final String PATH = "/internal/payments";
    private static final String FIND_PATH = PATH + "?orderType={t}&orderId={o}&memberId={m}";
    private static final String COMPENSATE_PATH = PATH + "/compensate?orderType={t}&orderId={o}&memberId={m}";
    private static final PaymentOrderType TYPE = PaymentOrderType.INDIVIDUAL_GROUP;
    private static final Long ORDER_ID = 100L;
    private static final Long MEMBER_ID = 1L;
    private static final long AMOUNT = 50000L;

    @Mock
    private InternalApiClient paymentServerApiClient;

    private HttpOrderPaymentClient paymentClient;

    @BeforeEach
    void setUp() {
        paymentClient = new HttpOrderPaymentClient(paymentServerApiClient);
    }

    @Test
    @DisplayName("차감 요청에 구분 · 주문 · 회원 · 금액을 담아 보내고 결제 서버의 결과를 그대로 돌려준다")
    void chargeReturnsPaymentServerResult() {
        PaymentResult paid = payment("PAID");
        given(paymentServerApiClient.post(eq(PATH), eq(new PaymentChargeRequest(TYPE, ORDER_ID, MEMBER_ID, AMOUNT)), any()))
                .willReturn(paid);

        assertThat(paymentClient.charge(TYPE, ORDER_ID, MEMBER_ID, AMOUNT)).isEqualTo(paid);
        then(paymentServerApiClient).should(never()).get(anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("결제 서버가 차감을 업무 거절하면(잔액 부족 등) 결제 여부를 다시 묻지 않고 그 예외를 그대로 던진다")
    void chargeRejectionPropagatesWithoutLookup() {
        ServiceException rejected = new ServiceException("400-INSUFFICIENT_BALANCE", "예치금이 부족합니다.");
        given(paymentServerApiClient.post(eq(PATH), any(), any())).willThrow(rejected);

        assertThatThrownBy(() -> paymentClient.charge(TYPE, ORDER_ID, MEMBER_ID, AMOUNT)).isSameAs(rejected);
        then(paymentServerApiClient).should(never()).get(anyString(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAID", "PARTIALLY_REFUNDED"})
    @DisplayName("차감 응답을 못 받았어도 결제 서버 재조회에서 결제됨으로 확인되면 그 결제를 돌려준다")
    void chargeTimeoutRecoversPaidPayment(String status) {
        PaymentResult found = payment(status);
        given(paymentServerApiClient.post(eq(PATH), any(), any())).willThrow(unavailable());
        given(paymentServerApiClient.get(eq(FIND_PATH), any(), eq(TYPE), eq(ORDER_ID), eq(MEMBER_ID))).willReturn(found);

        assertThat(paymentClient.charge(TYPE, ORDER_ID, MEMBER_ID, AMOUNT)).isEqualTo(found);
    }

    @Test
    @DisplayName("차감 응답을 못 받고 재조회에서 결제가 없으면(404) 처음의 응답 없음 예외를 던진다")
    void chargeTimeoutWithoutPaymentThrowsOriginal() {
        InternalServerUnavailableException timeout = unavailable();
        given(paymentServerApiClient.post(eq(PATH), any(), any())).willThrow(timeout);
        given(paymentServerApiClient.get(eq(FIND_PATH), any(), eq(TYPE), eq(ORDER_ID), eq(MEMBER_ID)))
                .willThrow(new ServiceException("404-PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다."));

        assertThatThrownBy(() -> paymentClient.charge(TYPE, ORDER_ID, MEMBER_ID, AMOUNT)).isSameAs(timeout);
    }

    @Test
    @DisplayName("차감 응답을 못 받고 재조회한 결제가 결제됨이 아니면(되돌림 등) 처음의 응답 없음 예외를 던진다")
    void chargeTimeoutWithUnpaidPaymentThrowsOriginal() {
        InternalServerUnavailableException timeout = unavailable();
        given(paymentServerApiClient.post(eq(PATH), any(), any())).willThrow(timeout);
        given(paymentServerApiClient.get(eq(FIND_PATH), any(), eq(TYPE), eq(ORDER_ID), eq(MEMBER_ID)))
                .willReturn(payment("CANCELED"));

        assertThatThrownBy(() -> paymentClient.charge(TYPE, ORDER_ID, MEMBER_ID, AMOUNT)).isSameAs(timeout);
    }

    @Test
    @DisplayName("차감 응답도 재조회 응답도 없으면 응답 없음 예외를 던져 결제됐는지 모른 채 성공으로 처리하지 않는다")
    void chargeTimeoutAndLookupUnavailableThrows() {
        given(paymentServerApiClient.post(eq(PATH), any(), any())).willThrow(unavailable());
        given(paymentServerApiClient.get(eq(FIND_PATH), any(), eq(TYPE), eq(ORDER_ID), eq(MEMBER_ID)))
                .willThrow(unavailable());

        assertThatThrownBy(() -> paymentClient.charge(TYPE, ORDER_ID, MEMBER_ID, AMOUNT))
                .isInstanceOf(InternalServerUnavailableException.class)
                .hasMessageContaining("503-PAYMENT_UNAVAILABLE");
    }

    @Test
    @DisplayName("결제 조회는 결제 서버가 찾은 결제를 돌려준다")
    void findPaymentReturnsFound() {
        PaymentResult paid = payment("PAID");
        given(paymentServerApiClient.get(eq(FIND_PATH), any(), eq(TYPE), eq(ORDER_ID), eq(MEMBER_ID))).willReturn(paid);

        assertThat(paymentClient.findPayment(TYPE, ORDER_ID, MEMBER_ID)).contains(paid);
    }

    @Test
    @DisplayName("결제 조회에서 결제 서버가 없음(404)으로 답하면 빈 결과를 돌려준다")
    void findPaymentNotFoundIsEmpty() {
        given(paymentServerApiClient.get(eq(FIND_PATH), any(), eq(TYPE), eq(ORDER_ID), eq(MEMBER_ID)))
                .willThrow(new ServiceException("404-PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다."));

        assertThat(paymentClient.findPayment(TYPE, ORDER_ID, MEMBER_ID)).isEmpty();
    }

    @Test
    @DisplayName("결제 조회에서 404가 아닌 오류는 없음으로 바꾸지 않고 그대로 던진다")
    void findPaymentOtherErrorPropagates() {
        ServiceException serverError = new ServiceException("500-PAYMENT_SERVER_ERROR", "결제 처리 중 오류가 발생했습니다.");
        given(paymentServerApiClient.get(eq(FIND_PATH), any(), eq(TYPE), eq(ORDER_ID), eq(MEMBER_ID))).willThrow(serverError);

        assertThatThrownBy(() -> paymentClient.findPayment(TYPE, ORDER_ID, MEMBER_ID)).isSameAs(serverError);
    }

    @Test
    @DisplayName("결제 조회에 응답이 없으면 없음으로 바꾸지 않고 응답 없음 예외를 그대로 던진다")
    void findPaymentUnavailablePropagates() {
        InternalServerUnavailableException timeout = unavailable();
        given(paymentServerApiClient.get(eq(FIND_PATH), any(), eq(TYPE), eq(ORDER_ID), eq(MEMBER_ID))).willThrow(timeout);

        assertThatThrownBy(() -> paymentClient.findPayment(TYPE, ORDER_ID, MEMBER_ID)).isSameAs(timeout);
    }

    @Test
    @DisplayName("예치금 잔액은 결제 서버 값을 돌려주고, 값이 비어 있으면 0으로 본다")
    void walletBalanceDefaultsToZero() {
        given(paymentServerApiClient.get(eq(PATH + "/wallet-balance?memberId={m}"), any(), eq(MEMBER_ID)))
                .willReturn(30000L, (Long) null);

        assertThat(paymentClient.getWalletBalance(MEMBER_ID)).isEqualTo(30000L);
        assertThat(paymentClient.getWalletBalance(MEMBER_ID)).isZero();
    }

    @Test
    @DisplayName("차감 되돌리기는 구분 · 주문 · 회원으로 결제 서버에 되돌림을 요청한다")
    void compensateChargeCallsPaymentServer() {
        paymentClient.compensateCharge(TYPE, ORDER_ID, MEMBER_ID);

        then(paymentServerApiClient).should().post(eq(COMPENSATE_PATH), isNull(), any(), eq(TYPE), eq(ORDER_ID), eq(MEMBER_ID));
    }

    @Test
    @DisplayName("차감 되돌리기가 응답 없음이나 업무 오류로 실패해도 예외를 던지지 않는다 (호출한 쪽의 원래 예외를 지킨다)")
    void compensateChargeSwallowsFailures() {
        given(paymentServerApiClient.post(eq(COMPENSATE_PATH), isNull(), any(), eq(TYPE), eq(ORDER_ID), eq(MEMBER_ID)))
                .willThrow(unavailable())
                .willThrow(new ServiceException("409-PAYMENT_ALREADY_REFUNDED", "이미 환불된 결제입니다."));

        assertThatCode(() -> paymentClient.compensateCharge(TYPE, ORDER_ID, MEMBER_ID)).doesNotThrowAnyException();
        assertThatCode(() -> paymentClient.compensateCharge(TYPE, ORDER_ID, MEMBER_ID)).doesNotThrowAnyException();
    }

    private static InternalServerUnavailableException unavailable() {
        return new InternalServerUnavailableException("PAYMENT", "결제", new SocketTimeoutException("Read timed out"));
    }

    private static PaymentResult payment(String status) {
        return new PaymentResult(500L, ORDER_ID, AMOUNT, 0L, AMOUNT, TYPE.name(), status,
                LocalDateTime.of(2026, 10, 19, 12, 1), null);
    }
}
