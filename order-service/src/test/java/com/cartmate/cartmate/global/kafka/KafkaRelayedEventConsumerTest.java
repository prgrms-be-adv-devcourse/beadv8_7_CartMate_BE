package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.global.outbox.DomainEventTypeRegistry;
import com.cartmate.cartmate.global.outbox.OutboxTopics;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseMemberLeftEvent;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class KafkaRelayedEventConsumerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 6, 10, 0);

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final KafkaRelayedEventConsumer consumer = new KafkaRelayedEventConsumer(
            new ConcurrentKafkaListenerContainerFactory<>(), List.of(), new DomainEventTypeRegistry(),
            new EventEnvelopeReader(objectMapper), publisher);

    @Test
    @DisplayName("구독한 모듈의 토픽만 고르고 다른 모듈의 토픽은 듣지 않는다")
    void topicsOfSubscribedModulesOnly() {
        List<String> cartOnly = consumer.topicsOf(new RelaySubscription("g", Set.of("cart")));
        List<String> cartAndOrder = consumer.topicsOf(new RelaySubscription("g", Set.of("cart", "order")));

        assertThat(cartOnly)
                .contains(OutboxTopics.of(SharedGroupPurchaseMemberLeftEvent.class))
                .doesNotContain(OutboxTopics.of(OrderPaidEvent.class))
                .allSatisfy(topic -> assertThat(topic).startsWith("cartmate.cart."));
        assertThat(cartAndOrder)
                .contains(OutboxTopics.of(SharedGroupPurchaseMemberLeftEvent.class), OutboxTopics.of(OrderPaidEvent.class))
                .allSatisfy(topic -> assertThat(topic).matches("cartmate\\.(cart|order)\\..+"));
        assertThat(consumer.topicsOf(new RelaySubscription("g", Set.of("no-such-module")))).isEmpty();
    }

    @Test
    @DisplayName("봉투의 eventType으로 타입을 되살려 Kafka 재전달(fromKafka=true)로 발행한다")
    void republishesAsKafkaDelivery() {
        SharedGroupPurchaseMemberLeftEvent event = new SharedGroupPurchaseMemberLeftEvent(7L, 70L, AT.minusDays(1), AT);
        EventEnvelope envelope = envelope("SharedGroupPurchaseMemberLeftEvent", objectMapper.writeValueAsString(event));

        consumer.onMessage(record(objectMapper.writeValueAsString(envelope)));

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(published.capture());
        assertThat(published.getValue()).isInstanceOfSatisfying(OutboxMessageRelayed.class, message -> {
            assertThat(message.fromKafka()).isTrue();
            assertThat(message.envelope()).isEqualTo(envelope);
            assertThat(message.event()).isEqualTo(event);
        });
    }

    @Test
    @DisplayName("레지스트리에 없는 eventType이면 발행하지 않고 예외를 던져 에러 핸들러(DLT)로 넘긴다")
    void unknownEventTypeThrows() {
        String value = objectMapper.writeValueAsString(envelope("NoSuchEvent", "{}"));

        assertThatThrownBy(() -> consumer.onMessage(record(value))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("깨진 JSON은 재시도하지 않는 JacksonException으로 실패해 바로 DLT로 간다")
    void brokenJsonIsNotRetryableKind() {
        assertThatThrownBy(() -> consumer.onMessage(record("{not-json")))
                .isInstanceOf(JacksonException.class);
        verifyNoInteractions(publisher);
    }

    private static EventEnvelope envelope(String eventType, String payload) {
        return new EventEnvelope("event-1", eventType, "cartmate.cart.shared-group-purchase-member-left",
                "SharedGroupPurchase", "7", AT, 1, payload);
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("cartmate.cart.shared-group-purchase-member-left", 0, 0L, "7", value);
    }
}
