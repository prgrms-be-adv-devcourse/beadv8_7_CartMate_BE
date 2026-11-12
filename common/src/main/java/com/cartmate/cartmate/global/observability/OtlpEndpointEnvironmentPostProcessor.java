package com.cartmate.cartmate.global.observability;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * OTLP 트레이스 주소: 환경 변수 {@code OTEL_EXPORTER_OTLP_TRACES_ENDPOINT}에 값이 <b>있을 때만</b> Boot 속성으로 넘긴다.
 *
 * <p>yaml에 {@code endpoint: ${OTEL_EXPORTER_OTLP_TRACES_ENDPOINT:}}처럼 빈 값을 두면 Boot가 내보내기를 만들다가
 * "Invalid endpoint"로 앱이 뜨지 않는다. 속성 자체가 없으면 내보내기를 만들지 않으므로(traceId는 로그에만),
 * 주소가 없는 로컬 · 테스트에서도 그대로 뜬다. yaml에 이 속성을 직접 적으면 그 값이 우선한다.
 */
public class OtlpEndpointEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String ENDPOINT_ENV = "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT";
    static final String ENDPOINT_PROPERTY = "management.opentelemetry.tracing.export.otlp.endpoint";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String endpoint = environment.getProperty(ENDPOINT_ENV);
        if (endpoint == null || endpoint.isBlank() || environment.containsProperty(ENDPOINT_PROPERTY)) {
            return;
        }
        environment.getPropertySources().addLast(
                new MapPropertySource("cartmateOtlpEndpoint", Map.of(ENDPOINT_PROPERTY, endpoint.trim())));
    }
}
