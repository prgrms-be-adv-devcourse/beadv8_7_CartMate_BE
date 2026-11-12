package com.cartmate.cartmate.global.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import java.util.HashMap;
import java.util.Map;

/**
 * 스레드가 바뀌는 곳(Outbox 저장 → 릴레이, Inbox 수신 → 처리)에서 traceId를 잇는 도우미.
 *
 * <ul>
 *   <li>저장할 때: 지금 trace를 W3C {@code traceparent} 문자열로 꺼내 행에 같이 저장한다 ({@link #current()})</li>
 *   <li>나중에 처리할 때: 그 값을 부모로 하는 span을 열고 그 안에서 처리한다 ({@link #runInSpan})</li>
 *   <li>값이 없으면(예전 행 · 추적 꺼짐) 새 trace로 시작하고, 처리는 그대로 한다</li>
 * </ul>
 */
public class TraceparentSupport {

    static final String TRACEPARENT = "traceparent";
    static final int MAX_LENGTH = 55; // W3C traceparent 버전 00 길이 (칸 크기와 같음)

    private final Tracer tracer;
    private final Propagator propagator;

    public TraceparentSupport(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    // 추적이 없는 환경(직접 만든 테스트 객체 등)용: 꺼내면 null, 처리는 span 없이 그대로
    public static TraceparentSupport noop() {
        return new TraceparentSupport(Tracer.NOOP, Propagator.NOOP);
    }

    /** 지금 trace의 traceparent. 진행 중인 trace가 없으면 null */
    public String current() {
        Span span = tracer.currentSpan();
        if (span == null || span.isNoop()) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(span.context(), carrier, Map::put);
        String value = carrier.get(TRACEPARENT);
        return value == null || value.length() > MAX_LENGTH ? null : value;
    }

    /**
     * traceparent를 부모로 하는 span 안에서 실행한다. null이거나 읽을 수 없는 값이면 새 trace.
     * 예외는 span에 기록하고 그대로 던진다 (처리 흐름은 바꾸지 않는다).
     */
    public void runInSpan(String spanName, String traceparent, Map<String, String> tags, Runnable work) {
        Span.Builder builder = traceparent == null || traceparent.isBlank()
                ? tracer.spanBuilder().setNoParent()
                : propagator.extract(Map.of(TRACEPARENT, traceparent), Map::get);
        tags.forEach(builder::tag);
        Span span = builder.name(spanName).start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            work.run();
        } catch (RuntimeException | Error e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
