package com.cartmate.cartmate.boundedContext.wallet.out.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayException;
import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayException.FailureType;
import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayPayment;
import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayPayment.Status;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletChargeGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Set;
import java.util.function.Supplier;

// 토스페이먼츠 결제 승인·조회 API 클라이언트. 토스의 요청·응답 형식은 이 패키지 안에서만 쓴다.
@Component
public class TossPaymentsClient implements WalletChargeGateway {

    private record TossConfirmRequest(String paymentKey, String orderId, Long amount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TossPaymentResponse(String paymentKey, String orderId, String status, Long totalAmount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TossErrorResponse(String code, String message) {
    }

    private static final String ALREADY_PROCESSED_CODE = "ALREADY_PROCESSED_PAYMENT";
    // 사용자의 결제가 거절된 것이 아니라 결제사 쪽 장애라서 결과를 알 수 없는 오류 코드
    private static final Set<String> UNKNOWN_CODES = Set.of("FAILED_INTERNAL_SYSTEM_PROCESSING", "PROVIDER_ERROR");

    private final RestClient restClient;

    public TossPaymentsClient(@Qualifier("tossRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ChargeGatewayPayment confirm(String paymentKey, String orderNumber, Long amount, String idempotencyKey) {
        return call(() -> restClient.post()
                .uri("/v1/payments/confirm")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TossConfirmRequest(paymentKey, orderNumber, amount))
                .retrieve()
                .body(TossPaymentResponse.class));
    }

    @Override
    public ChargeGatewayPayment fetch(String paymentKey) {
        return call(() -> restClient.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .retrieve()
                .body(TossPaymentResponse.class));
    }

    private ChargeGatewayPayment call(Supplier<TossPaymentResponse> request) {
        TossPaymentResponse response;
        try {
            response = request.get();
        } catch (RestClientResponseException e) {
            throw toException(e);
        } catch (RestClientException e) {
            // 연결 실패, 읽기 시간 초과, 응답 해석 실패. 결제사에서 처리됐는지 알 수 없다.
            throw new ChargeGatewayException(FailureType.UNKNOWN, null,
                    "토스페이먼츠 응답을 받지 못했습니다.", e);
        }

        if (response == null || response.paymentKey() == null || response.orderId() == null
                || response.totalAmount() == null) {
            throw new ChargeGatewayException(FailureType.UNKNOWN, null,
                    "토스페이먼츠 응답이 올바르지 않습니다.");
        }

        return new ChargeGatewayPayment(
                response.paymentKey(), response.orderId(), response.totalAmount(), toStatus(response.status()));
    }

    // 상태 코드와 오류 코드로 확정 실패, 이미 처리됨, 알 수 없음을 가른다.
    private ChargeGatewayException toException(RestClientResponseException e) {
        TossErrorResponse error = readError(e);
        String code = error == null ? null : error.code();
        String message = error == null || error.message() == null ? "토스페이먼츠 요청이 실패했습니다." : error.message();
        HttpStatusCode status = e.getStatusCode();

        if (ALREADY_PROCESSED_CODE.equals(code)) {
            return new ChargeGatewayException(FailureType.ALREADY_PROCESSED, code, message, e);
        }

        // 서버 오류, 인증·권한 오류(우리 설정 문제), 요청 제한, 결제사 장애는 결제 자체가 거절된 것이 아니다.
        boolean unknown = status.is5xxServerError()
                || status.value() == 401 || status.value() == 403 || status.value() == 429
                || (code != null && UNKNOWN_CODES.contains(code));
        if (unknown) {
            return new ChargeGatewayException(FailureType.UNKNOWN, code, message, e);
        }

        return new ChargeGatewayException(FailureType.REJECTED, code, message, e);
    }

    private TossErrorResponse readError(RestClientResponseException e) {
        try {
            return e.getResponseBodyAs(TossErrorResponse.class);
        } catch (RuntimeException ignored) {
            return null; // 본문이 JSON이 아니면(게이트웨이 오류 페이지 등) 상태 코드만으로 판단한다.
        }
    }

    private Status toStatus(String status) {
        if (status == null) {
            return Status.UNKNOWN;
        }
        return switch (status) {
            case "DONE" -> Status.DONE;
            case "READY", "IN_PROGRESS", "WAITING_FOR_DEPOSIT" -> Status.IN_PROGRESS;
            case "CANCELED", "PARTIAL_CANCELED" -> Status.CANCELED;
            case "ABORTED", "EXPIRED" -> Status.FAILED;
            default -> Status.UNKNOWN;
        };
    }
}
