package com.cartmate.cartmate.global.observability;

import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;

// 실제 OTel 추적기 (내보내기 없음). 스프링 없이 traceparent 왕복을 확인한다
final class TestTracing {

    final OtelTracer tracer;
    final OtelPropagator propagator;

    TestTracing() {
        io.opentelemetry.api.trace.Tracer otel = SdkTracerProvider.builder()
                .setSampler(Sampler.alwaysOn())
                .build()
                .get("test");
        this.tracer = new OtelTracer(otel, new OtelCurrentTraceContext(), event -> {
        });
        this.propagator = new OtelPropagator(ContextPropagators.create(W3CTraceContextPropagator.getInstance()), otel);
    }
}
