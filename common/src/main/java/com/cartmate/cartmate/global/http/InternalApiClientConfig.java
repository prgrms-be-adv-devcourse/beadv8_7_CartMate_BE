package com.cartmate.cartmate.global.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

// 대상 서버별 내부 API 클라이언트. 서버를 하나 더 나누면 여기에 빈을 하나 더 만든다.
// RestClient.Builder는 스프링 빈을 받는다 → 관찰이 붙어 호출마다 traceparent 헤더가 실린다 (traceId가 서버를 넘어 이어짐)
@Configuration
public class InternalApiClientConfig {

    // 결제 서버 (결제·예치금·정산). cartmate.payment.client=http 일 때만 만든다.
    @Bean
    @ConditionalOnProperty(name = "cartmate.payment.client", havingValue = "http")
    public InternalApiClient paymentServerApiClient(@Value("${cartmate.payment.base-url:}") String baseUrl,
                                                    @Value("${cartmate.internal.api-key}") String apiKey,
                                                    Environment environment, RestClient.Builder restClientBuilder) {
        return new InternalApiClient("PAYMENT", "결제", baseUrl, apiKey, environment, restClientBuilder);
    }

    @Bean
    public InternalApiClient orderServerApiClient(@Value("${cartmate.order-service.base-url:}") String baseUrl,
                                                  @Value("${cartmate.internal.api-key}") String apiKey,
                                                  Environment environment, RestClient.Builder restClientBuilder) {
        return new InternalApiClient("ORDER", "주문", baseUrl, apiKey, environment, restClientBuilder);
    }

    @Bean
    public InternalApiClient productServerApiClient(@Value("${cartmate.product-service.base-url:}") String baseUrl,
                                                    @Value("${cartmate.internal.api-key}") String apiKey,
                                                    Environment environment, RestClient.Builder restClientBuilder) {
        return new InternalApiClient("PRODUCT", "상품", baseUrl, apiKey, environment, restClientBuilder);
    }

    @Bean
    public InternalApiClient etcServerApiClient(@Value("${cartmate.etc-service.base-url:}") String baseUrl,
                                                @Value("${cartmate.internal.api-key}") String apiKey,
                                                Environment environment, RestClient.Builder restClientBuilder) {
        return new InternalApiClient("ETC", "배송", baseUrl, apiKey, environment, restClientBuilder);
    }

    @Bean
    public InternalApiClient memberServerApiClient(@Value("${cartmate.member-service.base-url:}") String baseUrl,
                                                   @Value("${cartmate.internal.api-key}") String apiKey,
                                                   Environment environment, RestClient.Builder restClientBuilder) {
        return new InternalApiClient("MEMBER", "회원", baseUrl, apiKey, environment, restClientBuilder);
    }
}
