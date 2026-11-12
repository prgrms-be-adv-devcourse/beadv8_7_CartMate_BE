package com.cartmate.cartmate.global.observability;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalServerUnavailableException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Sentry로 보낼 예외 고르기: 클래스가 아니라 결과 코드의 상태 숫자로
class SentryEventFilterTest {

    private final TestTracing tracing = new TestTracing();
    private final SentryEventFilter filter = new SentryEventFilter(tracing.tracer);

    @Test
    void 업무_예외_4xx는_보내지_않는다() {
        SentryEvent event = new SentryEvent(new ServiceException("409-PAYMENT_ALREADY_CANCELED", "이미 취소된 결제"));

        assertThat(filter.execute(event, new Hint())).isNull();
    }

    @Test
    void 업무_예외라도_503은_보낸다() {
        SentryEvent event = new SentryEvent(
                new InternalServerUnavailableException("PAYMENT", "결제", new RuntimeException("timeout")));

        assertThat(filter.execute(event, new Hint())).isSameAs(event);
    }

    @Test
    void 일반_예외는_보내고_지금_traceId를_태그로_붙인다() {
        SentryEvent event = new SentryEvent(new IllegalStateException("예상 못한 오류"));
        Span span = tracing.tracer.nextSpan().name("request").start();

        SentryEvent sent;
        try (Tracer.SpanInScope ignored = tracing.tracer.withSpan(span)) {
            sent = filter.execute(event, new Hint());
        } finally {
            span.end();
        }

        assertThat(sent).isSameAs(event);
        assertThat(sent.getTag(SentryEventFilter.TRACE_ID_TAG)).isEqualTo(span.context().traceId());
    }
}
