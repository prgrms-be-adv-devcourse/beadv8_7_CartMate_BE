package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.event.DomainEvent;
import com.cartmate.cartmate.shared.order.dto.DeliveryAddressInfo;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderConfirmedEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderSellerItemsCanceledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxEventFactoryTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-11-02T03:04:05.123456Z");

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final OutboxEventFactory factory = new OutboxEventFactory(objectMapper, Clock.fixed(NOW, ZONE));

    @Test
    @DisplayName("봉투: eventId는 UUID, occurredAt은 시계의 지금, version은 1, status는 NEW, aggregate는 이벤트가 준 값")
    void envelope() {
        OrderPaidEvent event = paidEvent(100L);

        OutboxEvent outbox = factory.create(event);

        assertThat(UUID.fromString(outbox.getEventId())).isNotNull();
        assertThat(outbox.getEventType()).isEqualTo("OrderPaidEvent");
        assertThat(outbox.getTopic()).isEqualTo("cartmate.order.order-paid");
        assertThat(outbox.getAggregateType()).isEqualTo("Order");
        assertThat(outbox.getAggregateId()).isEqualTo("100");
        assertThat(outbox.getOccurredAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZONE));
        assertThat(outbox.getCreatedAt()).isEqualTo(outbox.getOccurredAt());
        assertThat(outbox.getVersion()).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        assertThat(outbox.getAttempts()).isZero();
        assertThat(outbox.getPublishedAt()).isNull();
        assertThat(outbox.getPayload()).contains("\"orderNumber\":\"ORD-100\"");
    }

    @Test
    @DisplayName("eventId는 발행마다 다르다")
    void eventIdIsUniquePerPublish() {
        OrderPaidEvent event = paidEvent(100L);

        assertThat(factory.create(event).getEventId()).isNotEqualTo(factory.create(event).getEventId());
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("topics")
    @DisplayName("토픽 규칙: cartmate.<발행 모듈>.<이벤트 kebab-case>")
    void topicRule(Class<?> eventType, String expectedTopic) {
        assertThat(OutboxTopics.of(eventType)).isEqualTo(expectedTopic);
    }

    static Stream<Arguments> topics() {
        return Stream.of(
                Arguments.of(OrderPaidEvent.class, "cartmate.order.order-paid"),
                Arguments.of(OrderCanceledEvent.class, "cartmate.order.order-canceled"),
                Arguments.of(OrderSellerItemsCanceledEvent.class, "cartmate.order.order-seller-items-canceled"),
                Arguments.of(SharedGroupOrderConfirmedEvent.class, "cartmate.order.shared-group-order-confirmed"),
                Arguments.of(SharedGroupOrderCanceledEvent.class, "cartmate.order.shared-group-order-canceled"),
                Arguments.of(SharedGroupOrderSellerItemsCanceledEvent.class,
                        "cartmate.order.shared-group-order-seller-items-canceled"),
                Arguments.of(SharedGroupPurchaseClosedEvent.class, "cartmate.cart.shared-group-purchase-closed")
        );
    }

    @Test
    @DisplayName("shared.<모듈>.event 패키지 밖의 클래스는 토픽을 만들 수 없다")
    void topicRejectsClassOutsideSharedEventPackage() {
        assertThatThrownBy(() -> OutboxTopics.of(OutboxEventFactoryTest.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    @DisplayName("직렬화 · 역직렬화 왕복: payload를 원래 클래스로 읽으면 같은 이벤트다 (LocalDateTime · 중첩 record · 목록 포함)")
    void payloadRoundTrip(DomainEvent event) {
        OutboxEvent outbox = factory.create(event);

        Object restored = objectMapper.readValue(outbox.getPayload(), event.getClass());

        assertThat(restored).isEqualTo(event);
    }

    static Stream<Arguments> events() {
        LocalDateTime at = LocalDateTime.of(2026, 11, 2, 12, 34, 56, 789_000_000);
        return Stream.of(
                Arguments.of(paidEvent(100L)),
                Arguments.of(new OrderCanceledEvent(100L, 1L, "PERSONAL", "BUYER_REQUEST", true, at)),
                Arguments.of(new OrderSellerItemsCanceledEvent(100L, 1L, 7L, 12_000L, at)),
                Arguments.of(new SharedGroupOrderConfirmedEvent(200L, 300L, 1L, at,
                        new com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress(
                                "홍길동", "010-1234-5678", "12345", "서울시", "101호"),
                        List.of(new SharedGroupOrderConfirmedEvent.Item(1L, 2L, 3L, 7L, "상품", null, 2, 5_000L)))),
                Arguments.of(new SharedGroupOrderCanceledEvent(200L, 300L, "DEADLINE_EXPIRED", at, List.of(1L, 2L))),
                Arguments.of(new SharedGroupOrderSellerItemsCanceledEvent(200L, 7L, 12_000L, at)),
                Arguments.of(new SharedGroupPurchaseClosedEvent(300L, 1L, List.of(1L, 2L),
                        List.of(new SharedGroupPurchaseClosedEvent.Item(2L, 3L, 2)), at))
        );
    }

    @Test
    @DisplayName("aggregateId가 비어 있으면 봉투를 만들지 않는다")
    void rejectsBlankAggregate() {
        DomainEvent noAggregate = new DomainEvent() {
            @Override
            public String aggregateType() {
                return "Order";
            }

            @Override
            public String aggregateId() {
                return " ";
            }
        };

        assertThatThrownBy(() -> factory.create(noAggregate)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("실패 기록: attempts가 늘고 last_error가 1000자로 잘리며, 다음 시도가 백오프만큼 밀리고, 최대 시도에 이르면 FAILED")
    void recordFailure() {
        OutboxEvent outbox = factory.create(paidEvent(100L));
        OutboxRelayProperties policy = policy(3);
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 13, 0);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(outbox.getOccurredAt());

        assertThat(outbox.recordFailure("x".repeat(2_000), policy, now)).isFalse();
        assertThat(outbox.getAttempts()).isEqualTo(1);
        assertThat(outbox.getLastError()).hasSize(1_000);
        assertThat(outbox.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(1));

        assertThat(outbox.recordFailure("again", policy, now)).isFalse();
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(2));

        assertThat(outbox.recordFailure("boom", policy, now)).isTrue();
        assertThat(outbox.getAttempts()).isEqualTo(3);
        assertThat(outbox.getLastError()).isEqualTo("boom");
        assertThat(outbox.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
    }

    @Test
    @DisplayName("백오프: 1초에서 두 배씩 늘고 10분에서 멈춘다. 기본값이면 마지막 시도까지 약 57분")
    void backoffDoublesUpToCap() {
        OutboxRelayProperties policy = policy(15);

        assertThat(policy.backoffAfter(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.backoffAfter(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.backoffAfter(10)).isEqualTo(Duration.ofSeconds(512));
        assertThat(policy.backoffAfter(11)).isEqualTo(Duration.ofMinutes(10));
        assertThat(policy.backoffAfter(1_000)).isEqualTo(Duration.ofMinutes(10));
        Duration total = Duration.ZERO;
        for (int attempts = 1; attempts < policy.maxAttempts(); attempts++) {
            total = total.plus(policy.backoffAfter(attempts));
        }
        assertThat(total).isBetween(Duration.ofMinutes(55), Duration.ofMinutes(60));
    }

    @Test
    @DisplayName("다시 해도 소용없는 실패: 시도 횟수와 상관없이 바로 FAILED")
    void markFailedImmediately() {
        OutboxEvent outbox = factory.create(paidEvent(100L));

        assertThat(outbox.markFailed("unknown type")).isTrue();

        assertThat(outbox.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(outbox.getAttempts()).isEqualTo(1);
        assertThat(outbox.getLastError()).isEqualTo("unknown type");
    }

    @Test
    @DisplayName("NEW가 아닌 행은 성공 · 실패 기록이 상태를 바꾸지 않는다 (다른 노드가 먼저 처리한 행)")
    void nonNewRowIsUntouched() {
        OutboxEvent outbox = factory.create(paidEvent(100L));
        LocalDateTime publishedAt = LocalDateTime.of(2026, 11, 2, 13, 0);
        outbox.markPublished(publishedAt);

        assertThat(outbox.recordFailure("late", policy(3), publishedAt)).isFalse();
        assertThat(outbox.markFailed("late")).isFalse();

        assertThat(outbox.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outbox.getAttempts()).isZero();
        assertThat(outbox.getLastError()).isNull();
    }

    @Test
    @DisplayName("발행 성공: PUBLISHED로 바뀌고 published_at이 찍히며 last_error는 지워진다")
    void markPublished() {
        OutboxEvent outbox = factory.create(paidEvent(100L));
        LocalDateTime publishedAt = LocalDateTime.of(2026, 11, 2, 13, 0);
        outbox.recordFailure("transient", policy(10), publishedAt);

        outbox.markPublished(publishedAt);

        assertThat(outbox.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outbox.getPublishedAt()).isEqualTo(publishedAt);
        assertThat(outbox.getLastError()).isNull();
        assertThat(outbox.getAttempts()).isEqualTo(1);
    }

    private static OutboxRelayProperties policy(int maxAttempts) {
        return new OutboxRelayProperties(Duration.ofSeconds(1), Duration.ofSeconds(1), 100, maxAttempts,
                Duration.ofSeconds(1), Duration.ofMinutes(10));
    }

    private static OrderPaidEvent paidEvent(Long orderId) {
        return new OrderPaidEvent(
                orderId, "ORD-" + orderId, 1L, "PERSONAL", null,
                LocalDateTime.of(2026, 11, 2, 12, 0, 0, 123_000_000),
                new DeliveryAddressInfo("홍길동", "010-1234-5678", "12345", "서울시", "101호"),
                List.of(new OrderPaidEvent.Item(1L, 2L, 3L, 7L, "상품", "옵션", 1, 10_000L))
        );
    }
}
