package com.cartmate.cartmate.boundedContext.order.out;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.security.InternalApiKeyFilter;
import com.cartmate.cartmate.shared.payment.dto.PaymentChargeRequest;
import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

/**
 * 결제 서버 내부 API(/internal/payments)를 HTTP로 부르는 구현. 서버 분리 후의 통신 방식과 같다.
 *
 * <p>통신 공통 처리는 {@link #call} 한곳에서 한다.
 * <ul>
 *   <li>응답이 오면: 성공이면 data, 실패면 결제 서버의 resultCode·메시지 그대로 ServiceException (예: 잔액 부족)</li>
 *   <li>응답이 없으면(연결 실패·시간 초과): {@link PaymentServerUnavailableException} — 그대로 두면 503 안내</li>
 * </ul>
 * 각 메서드는 업무 의미만 남긴다. 연결 실패를 다르게 다뤄야 하는 곳만 따로 잡는다:
 * 차감은 돈이 빠졌는지 모르는 상태라 결제 여부를 다시 조회하고, 되돌리기는 원래 오류를 덮지 않게 로그만 남긴다.
 *
 * <p>cartmate.payment.base-url 이 비어 있으면 자기 자신(같은 앱)을 부른다 → 서버를 나누기 전에도 http 모드를 확인할 수 있다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cartmate.payment.client", havingValue = "http")
public class HttpOrderPaymentClient implements OrderPaymentClient {

    private static final String PATH = "/internal/payments";

    private final RestClient restClient;
    private final String baseUrl;
    private final Environment environment;

    public HttpOrderPaymentClient(@Value("${cartmate.payment.base-url:}") String baseUrl,
                                  @Value("${cartmate.internal.api-key}") String apiKey,
                                  Environment environment) {
        this.baseUrl = baseUrl;
        this.environment = environment;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(InternalApiKeyFilter.HEADER, apiKey)
                .build();
    }

    @Override
    public PaymentResult charge(PaymentOrderType orderType, Long orderId, Long memberId, long amount) {
        try {
            return call(restClient.post().uri(url(PATH))
                            .body(new PaymentChargeRequest(orderType, orderId, memberId, amount)),
                    new ParameterizedTypeReference<InternalResponse<PaymentResult>>() {});
        } catch (PaymentServerUnavailableException e) {
            // 응답을 못 받음 → 실제로 빠졌는지 같은 키로 확인. 조회도 안 되면 그대로 503
            log.warn("결제 차감 응답 없음, 결제 여부 확인: {} #{} 회원 #{}", orderType, orderId, memberId);
            return findPayment(orderType, orderId, memberId)
                    .filter(PaymentResult::isPaid)
                    .orElseThrow(() -> e);
        }
    }

    @Override
    public Optional<PaymentResult> findPayment(PaymentOrderType orderType, Long orderId, Long memberId) {
        try {
            return Optional.of(call(restClient.get().uri(url(PATH) + "?orderType={t}&orderId={o}&memberId={m}",
                    orderType, orderId, memberId), new ParameterizedTypeReference<InternalResponse<PaymentResult>>() {}));
        } catch (PaymentServerUnavailableException e) {
            throw e;
        } catch (ServiceException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("404")) {
                return Optional.empty(); // 결제 없음
            }
            throw e;
        }
    }

    @Override
    public long getWalletBalance(Long memberId) {
        Long balance = call(restClient.get().uri(url(PATH) + "/wallet-balance?memberId={m}", memberId),
                new ParameterizedTypeReference<InternalResponse<Long>>() {});
        return balance == null ? 0 : balance;
    }

    // 되돌리기가 실패해도 원래 오류(왜 반영이 거절됐는지)를 덮지 않는다.
    // 대신 크게 남겨서 사람이 확인하게 한다 — 돈은 빠졌는데 주문은 결제 대기인 상태.
    @Override
    public void compensateCharge(PaymentOrderType orderType, Long orderId, Long memberId) {
        try {
            call(restClient.post().uri(url(PATH) + "/compensate?orderType={t}&orderId={o}&memberId={m}",
                    orderType, orderId, memberId), new ParameterizedTypeReference<InternalResponse<Void>>() {});
        } catch (RuntimeException e) {
            log.error("결제 되돌리기 실패 — 수동 확인 필요: {} #{} 회원 #{}", orderType, orderId, memberId, e);
        }
    }

    // ===== 통신 공통 처리 =====
    private <T> T call(RestClient.RequestHeadersSpec<?> spec, ParameterizedTypeReference<InternalResponse<T>> type) {
        try {
            return spec.exchange((request, response) -> {
                InternalResponse<T> body = response.bodyTo(type);
                if (response.getStatusCode().is2xxSuccessful()) {
                    return body == null ? null : body.data();
                }
                if (body == null || body.resultCode() == null) {
                    throw new ServiceException(response.getStatusCode().value() + "-PAYMENT_SERVER_ERROR",
                            "결제 처리 중 오류가 발생했습니다.");
                }
                throw new ServiceException(body.resultCode(), body.msg()); // 결제 서버의 오류를 그대로 (예: 잔액 부족)
            });
        } catch (ResourceAccessException e) {
            // 응답 자체가 없음 (연결 거절, 시간 초과)
            log.warn("결제 서버 연결 실패: {}", e.getMessage());
            throw new PaymentServerUnavailableException(e);
        }
    }

    private String url(String path) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl + path;
        }
        // base-url이 없으면 같은 앱 자신 (서버 분리 전 http 모드 확인용)
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        return "http://localhost:" + port + path;
    }

    public record InternalResponse<T>(String resultCode, String msg, T data) {
    }

    /**
     * 결제 서버에 연결하지 못함 (응답 자체가 없음). ServiceException이라 잡지 않으면 503 안내로 나간다.
     * 차감처럼 "됐는지 모르는" 호출은 이 예외를 잡아 따로 판단한다.
     */
    static class PaymentServerUnavailableException extends ServiceException {
        PaymentServerUnavailableException(Throwable cause) {
            super("503-PAYMENT_UNAVAILABLE", "결제 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
            initCause(cause);
        }
    }
}