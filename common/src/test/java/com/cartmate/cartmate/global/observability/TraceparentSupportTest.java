package com.cartmate.cartmate.global.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Outbox 저장 → 릴레이, Inbox 수신 → 처리처럼 스레드가 바뀌어도 traceId가 이어지는지
class TraceparentSupportTest {

    private final TestTracing tracing = new TestTracing();
    private final TraceparentSupport support = new TraceparentSupport(tracing.tracer, tracing.propagator);

    @Test
    void 꺼낸_traceparent를_부모로_하면_같은_traceId로_이어진다() {
        Span request = tracing.tracer.nextSpan().name("request").start();
        String saved;
        try (Tracer.SpanInScope ignored = tracing.tracer.withSpan(request)) {
            saved = support.current(); // 저장 시점 (요청 스레드)
        } finally {
            request.end();
        }
        assertThat(saved).hasSize(TraceparentSupport.MAX_LENGTH).contains(request.context().traceId());

        AtomicReference<Span> inside = new AtomicReference<>();
        support.runInSpan("outbox.relay", saved, Map.of(), () -> inside.set(tracing.tracer.currentSpan())); // 나중에 (릴레이 스레드)

        assertThat(inside.get().context().traceId()).isEqualTo(request.context().traceId());
        assertThat(inside.get().context().parentId()).isEqualTo(request.context().spanId());
    }

    @Test
    void 값이_없으면_지금_진행_중인_trace와_상관없이_새_trace로_처리한다() {
        Span other = tracing.tracer.nextSpan().name("other").start();
        AtomicReference<Span> inside = new AtomicReference<>();
        try (Tracer.SpanInScope ignored = tracing.tracer.withSpan(other)) {
            support.runInSpan("inbox.process", null, Map.of(), () -> inside.set(tracing.tracer.currentSpan()));
        } finally {
            other.end();
        }

        assertThat(inside.get()).isNotNull();
        assertThat(inside.get().context().traceId()).isNotEqualTo(other.context().traceId());
    }

    @Test
    void 진행_중인_trace가_없으면_null() {
        assertThat(support.current()).isNull();
    }

    @Test
    void 처리_중_예외는_그대로_던진다() {
        RuntimeException failure = new IllegalStateException("처리 실패");

        assertThatThrownBy(() -> support.runInSpan("inbox.process", null, Map.of(), () -> {
            throw failure;
        })).isSameAs(failure);
        assertThat(tracing.tracer.currentSpan()).isNull(); // span 범위가 닫혔다
    }

    @Test
    void 추적이_꺼져_있어도_처리는_그대로_한다() {
        TraceparentSupport noop = TraceparentSupport.noop();
        AtomicReference<Boolean> ran = new AtomicReference<>(false);

        noop.runInSpan("outbox.relay", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01", Map.of(),
                () -> ran.set(true));

        assertThat(ran.get()).isTrue();
        assertThat(noop.current()).isNull();
    }
}
