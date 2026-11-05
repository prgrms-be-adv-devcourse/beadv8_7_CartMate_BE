package com.cartmate.cartmate.global.http;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.security.InternalApiKeyFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 서버 간 내부 API(/internal/**) 호출 공통 처리. 대상 서버마다 하나씩 만들어 어댑터들이 같이 쓴다.
 *
 * <ul>
 *   <li>시간 제한: 연결 2초, 응답 5초</li>
 *   <li>내부 API 키 헤더</li>
 *   <li>응답 해석: 우리 RsData 형식({resultCode, msg, data}). 실패면 상대 서버의 resultCode·메시지 그대로 ServiceException</li>
 *   <li>응답이 없으면(연결 실패·시간 초과) {@link InternalServerUnavailableException} — 그대로 두면 503 안내</li>
 * </ul>
 * 어댑터에는 업무 의미만 남긴다. 연결 실패를 다르게 다뤄야 하는 호출(예: 결제 차감)만 이 예외를 따로 잡는다.
 *
 * <p>baseUrl이 비어 있으면 같은 앱 자신을 부른다 → 서버를 나누기 전에도 http 모드로 분리 후 통신을 확인할 수 있다.
 */
@Slf4j
public class InternalApiClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final Environment environment;
    private final String serverCode;   // 오류 코드용 (예: PAYMENT → 503-PAYMENT_UNAVAILABLE)
    private final String serverName;   // 안내 문구용 (예: 결제)

    public InternalApiClient(String serverCode, String serverName, String baseUrl, String apiKey, Environment environment) {
        this.serverCode = serverCode;
        this.serverName = serverName;
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

    public <T> T get(String path, ParameterizedTypeReference<InternalResponse<T>> type, Object... uriVariables) {
        return call(restClient.get().uri(url(path), uriVariables), type);
    }

    public <T> T post(String path, Object body, ParameterizedTypeReference<InternalResponse<T>> type, Object... uriVariables) {
        RestClient.RequestBodySpec spec = restClient.post().uri(url(path), uriVariables);
        return call(body == null ? spec : spec.body(body), type);
    }

    // 상대 서버가 "없음(404)"으로 답했는지 (메시지 문자열이 아니라 결과 코드의 상태 숫자로 판단)
    public static boolean isNotFound(ServiceException e) {
        return e.getRsData().getStatusCode() == 404;
    }

    private <T> T call(RestClient.RequestHeadersSpec<?> spec, ParameterizedTypeReference<InternalResponse<T>> type) {
        try {
            return spec.exchange((request, response) -> {
                InternalResponse<T> body = response.bodyTo(type);
                if (response.getStatusCode().is2xxSuccessful()) {
                    return body == null ? null : body.data();
                }
                if (body == null || body.resultCode() == null) {
                    throw new ServiceException(response.getStatusCode().value() + "-" + serverCode + "_SERVER_ERROR",
                            serverName + " 처리 중 오류가 발생했습니다.");
                }
                throw new ServiceException(body.resultCode(), body.msg()); // 상대 서버의 오류를 그대로
            });
        } catch (ResourceAccessException e) {
            log.warn("{} 서버 연결 실패: {}", serverName, e.getMessage());
            throw new InternalServerUnavailableException(serverCode, serverName, e);
        }
    }

    private String url(String path) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl + path;
        }
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        return "http://localhost:" + port + path;
    }

    public record InternalResponse<T>(String resultCode, String msg, T data) {
    }

    /**
     * 상대 서버에 연결하지 못함 (응답 자체가 없음). ServiceException이라 잡지 않으면 503 안내로 나간다.
     * "됐는지 모르는" 호출(예: 결제 차감)은 이 예외를 잡아 따로 판단한다.
     */
    public static class InternalServerUnavailableException extends ServiceException {
        public InternalServerUnavailableException(String serverCode, String serverName, Throwable cause) {
            super("503-" + serverCode + "_UNAVAILABLE", serverName + " 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
            initCause(cause);
        }
    }
}