package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
class GeminiApi {

    static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    GeminiApi(@Value("${GEMINI_API_KEY:}") String apiKey) {
        this(RestClient.builder().requestFactory(requestFactory()), apiKey);
        if (!isConfigured()) {
            log.warn("GEMINI_API_KEY is not set; product embedding and labeling are disabled");
        }
    }

    GeminiApi(RestClient.Builder builder, String apiKey) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.apiKey = apiKey;
    }

    boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    Map<?, ?> post(String path, Object body) {
        if (!isConfigured()) {
            throw new GeminiNotConfiguredException();
        }
        Map<?, ?> response;
        try {
            response = restClient.post()
                    .uri(path)
                    .header("x-goog-api-key", apiKey)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            if (isTriableOnAnotherModel(e.getStatusCode().value())) {
                throw new GeminiUnavailableException("Gemini " + e.getStatusCode().value() + " on " + path, e);
            }
            throw e;
        } catch (ResourceAccessException e) {
            throw new GeminiUnavailableException("Gemini unreachable on " + path, e);
        }
        if (response == null) {
            throw new GeminiUnavailableException("Gemini empty body on " + path, null);
        }
        return response;
    }

    private static boolean isTriableOnAnotherModel(int status) {
        return status == HttpStatus.TOO_MANY_REQUESTS.value()
                || status == HttpStatus.NOT_FOUND.value()
                || status >= 500;
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }

    static class GeminiUnavailableException extends RuntimeException {

        GeminiUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static class GeminiNotConfiguredException extends RuntimeException {

        GeminiNotConfiguredException() {
            super("GEMINI_API_KEY is not set");
        }
    }
}
