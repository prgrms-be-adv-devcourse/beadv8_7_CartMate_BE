package com.cartmate.cartmate.global.observability;

import com.cartmate.cartmate.global.exception.ServiceException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;

/**
 * Sentry로 보내기 직전 거르기.
 *
 * <ul>
 *   <li>업무 예외({@link ServiceException})는 결과 코드의 상태 숫자가 500 미만이면 보내지 않는다 (사용자 실수 · 업무 규칙).
 *       클래스로 거르지 않는다 → 503 {@code InternalServerUnavailableException}은 보낸다</li>
 *   <li>지금 traceId를 태그로 붙인다 → Sentry에서 Grafana(Tempo · Loki)로 찾아간다</li>
 * </ul>
 */
public class SentryEventFilter implements SentryOptions.BeforeSendCallback {

    static final String TRACE_ID_TAG = "traceId";

    private final Tracer tracer;

    public SentryEventFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public SentryEvent execute(SentryEvent event, Hint hint) {
        if (isClientError(event.getThrowable())) {
            return null;
        }
        Span span = tracer.currentSpan();
        if (span != null && !span.isNoop()) {
            event.setTag(TRACE_ID_TAG, span.context().traceId());
        }
        return event;
    }

    private static boolean isClientError(Throwable throwable) {
        if (!(throwable instanceof ServiceException e)) {
            return false;
        }
        try {
            return e.getRsData().getStatusCode() < 500;
        } catch (RuntimeException unreadable) {
            return false; // 결과 코드를 못 읽으면 일단 보낸다
        }
    }
}
