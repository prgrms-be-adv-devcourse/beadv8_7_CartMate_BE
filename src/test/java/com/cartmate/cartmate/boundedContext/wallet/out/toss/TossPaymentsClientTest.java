package com.cartmate.cartmate.boundedContext.wallet.out.toss;

import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayException;
import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayException.FailureType;
import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayPayment;
import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayPayment.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TossPaymentsClientTest {

    private static final String BASE_URL = "https://api.tosspayments.com";
    private static final String SECRET_KEY = "test_gsk_secret";
    private static final String ORDER_NUMBER = "Charge_11111111-2222-3333-4444-555555555555";

    private MockRestServiceServer server;
    private TossPaymentsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TossPaymentsClient(TossConfig.restClient(builder, new TossProperties(BASE_URL, SECRET_KEY)));
    }

    private static String payment(String status) {
        return """
                {"paymentKey":"pk_1","orderId":"%s","status":"%s","totalAmount":10000,
                 "method":"카드","currency":"KRW","approvedAt":"2026-10-20T10:00:00+09:00"}
                """.formatted(ORDER_NUMBER, status);
    }

    private static String error(String code) {
        return """
                {"code":"%s","message":"오류 메시지"}
                """.formatted(code);
    }

    @Test
    @DisplayName("승인 요청은 시크릿 키 Basic 인증과 멱등 키를 담아 주문번호·금액·결제키를 보낸다")
    void confirmRequest() {
        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", expectedAuth))
                .andExpect(header("Idempotency-Key", "idem-1"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"paymentKey":"pk_1","orderId":"%s","amount":10000}
                        """.formatted(ORDER_NUMBER)))
                .andRespond(withSuccess(payment("DONE"), MediaType.APPLICATION_JSON));

        ChargeGatewayPayment result = client.confirm("pk_1", ORDER_NUMBER, 10_000L, "idem-1");

        assertThat(result.paymentKey()).isEqualTo("pk_1");
        assertThat(result.orderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(result.amount()).isEqualTo(10_000L);
        assertThat(result.status()).isEqualTo(Status.DONE);
        server.verify();
    }

    @Test
    @DisplayName("조회는 결제키로 GET 요청을 보내고 쓰지 않는 응답 필드는 무시한다")
    void fetchRequest() {
        server.expect(requestTo(BASE_URL + "/v1/payments/pk_1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(payment("DONE"), MediaType.APPLICATION_JSON));

        ChargeGatewayPayment result = client.fetch("pk_1");

        assertThat(result.orderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(result.status()).isEqualTo(Status.DONE);
        server.verify();
    }

    @ParameterizedTest(name = "토스 상태 {0} → {1}")
    @CsvSource({
            "DONE, DONE",
            "READY, IN_PROGRESS",
            "IN_PROGRESS, IN_PROGRESS",
            "WAITING_FOR_DEPOSIT, IN_PROGRESS",
            "CANCELED, CANCELED",
            "PARTIAL_CANCELED, CANCELED",
            "ABORTED, FAILED",
            "EXPIRED, FAILED",
            "SOMETHING_NEW, UNKNOWN"
    })
    @DisplayName("토스 결제 상태를 충전에서 쓰는 상태로 바꾼다")
    void statusMapping(String tossStatus, Status expected) {
        server.expect(requestTo(BASE_URL + "/v1/payments/pk_1"))
                .andRespond(withSuccess(payment(tossStatus), MediaType.APPLICATION_JSON));

        assertThat(client.fetch("pk_1").status()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
            "400, ALREADY_PROCESSED_PAYMENT, ALREADY_PROCESSED",
            "400, REJECT_CARD_PAYMENT, REJECTED",
            "400, NOT_FOUND_PAYMENT_SESSION, REJECTED",
            "400, INVALID_REQUEST, REJECTED",
            "404, NOT_FOUND_PAYMENT, REJECTED",
            "400, PROVIDER_ERROR, UNKNOWN",
            "401, UNAUTHORIZED_KEY, UNKNOWN",
            "403, FORBIDDEN_REQUEST, UNKNOWN",
            "429, TOO_MANY_REQUESTS, UNKNOWN",
            "500, FAILED_INTERNAL_SYSTEM_PROCESSING, UNKNOWN",
            "502, SOME_GATEWAY_ERROR, UNKNOWN"
    })
    @DisplayName("승인 실패를 확정 실패, 이미 처리됨, 알 수 없음으로 가른다")
    void confirmFailureType(int status, String code, FailureType expected) {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON).body(error(code)));

        assertThatThrownBy(() -> client.confirm("pk_1", ORDER_NUMBER, 10_000L, "idem-1"))
                .isInstanceOfSatisfying(ChargeGatewayException.class, e -> {
                    assertThat(e.getFailureType()).isEqualTo(expected);
                    assertThat(e.getCode()).isEqualTo(code);
                    assertThat(e.getMessage()).isEqualTo("오류 메시지");
                });
    }

    @Test
    @DisplayName("오류 본문이 JSON이 아니면 상태 코드만으로 판단한다")
    void nonJsonErrorBody() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.TEXT_HTML).body("<html>Bad Gateway</html>"));

        assertThatThrownBy(() -> client.confirm("pk_1", ORDER_NUMBER, 10_000L, "idem-1"))
                .isInstanceOfSatisfying(ChargeGatewayException.class, e -> {
                    assertThat(e.getFailureType()).isEqualTo(FailureType.UNKNOWN);
                    assertThat(e.getCode()).isNull();
                });
    }

    @Test
    @DisplayName("연결 실패나 읽기 시간 초과는 알 수 없음으로 처리한다")
    void ioFailure() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withException(new IOException("Read timed out")));

        assertThatThrownBy(() -> client.confirm("pk_1", ORDER_NUMBER, 10_000L, "idem-1"))
                .isInstanceOfSatisfying(ChargeGatewayException.class,
                        e -> assertThat(e.getFailureType()).isEqualTo(FailureType.UNKNOWN));
    }

    @Test
    @DisplayName("성공 응답이어도 필수 값이 비어 있으면 알 수 없음으로 처리한다")
    void incompleteResponse() {
        server.expect(requestTo(BASE_URL + "/v1/payments/pk_1"))
                .andRespond(withSuccess("{\"status\":\"DONE\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetch("pk_1"))
                .isInstanceOfSatisfying(ChargeGatewayException.class,
                        e -> assertThat(e.getFailureType()).isEqualTo(FailureType.UNKNOWN));
    }

    @Test
    @DisplayName("본문이 없는 성공 응답은 알 수 없음으로 처리한다")
    void emptyResponse() {
        server.expect(requestTo(BASE_URL + "/v1/payments/pk_1"))
                .andRespond(withSuccess());

        assertThatThrownBy(() -> client.fetch("pk_1"))
                .isInstanceOfSatisfying(ChargeGatewayException.class,
                        e -> assertThat(e.getFailureType()).isEqualTo(FailureType.UNKNOWN));
    }
}
