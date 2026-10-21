package com.cartmate.cartmate.boundedContext.wallet.out.toss;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

// 토스페이먼츠 호출에 쓰는 RestClient. 결제 승인·조회와 이후 웹훅 관련 호출이 같은 인증·타임아웃 설정을 공유한다.
@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class TossConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public RestClient tossRestClient(TossProperties properties) {
        return restClient(RestClient.builder().requestFactory(requestFactory()), properties);
    }

    // 테스트가 MockRestServiceServer에 연결한 빌더를 넘길 수 있도록 요청 팩토리는 건드리지 않는다.
    static RestClient restClient(RestClient.Builder builder, TossProperties properties) {
        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth(properties.secretKey()))
                .build();
    }

    private static String basicAuth(String secretKey) {
        // 토스는 시크릿 키 뒤에 콜론을 붙여 Base64로 인코딩한 값을 Basic 인증으로 받는다.
        String credentials = secretKey + ":";
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static ClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
