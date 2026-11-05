package com.cartmate.cartmate.global.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

// 대상 서버별 내부 API 클라이언트. 서버를 하나 더 나누면 여기에 빈을 하나 더 만든다.
@Configuration
public class InternalApiClientConfig {

    // 결제 서버 (결제·예치금·정산). cartmate.payment.client=http 일 때만 만든다.
    @Bean
    @ConditionalOnProperty(name = "cartmate.payment.client", havingValue = "http")
    public InternalApiClient paymentServerApiClient(@Value("${cartmate.payment.base-url:}") String baseUrl,
                                                    @Value("${cartmate.internal.api-key}") String apiKey,
                                                    Environment environment) {
        return new InternalApiClient("PAYMENT", "결제", baseUrl, apiKey, environment);
    }
}