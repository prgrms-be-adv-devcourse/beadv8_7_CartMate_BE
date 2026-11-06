package com.cartmate.cartmate.shared.cart.event;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.kafka.EventEnvelopeReader;
import com.cartmate.cartmate.global.outbox.OutboxEvent;
import com.cartmate.cartmate.global.outbox.OutboxEventFactory;
import com.cartmate.cartmate.shared.cart.IndividualGroupPurchaseCloseReason;
import com.cartmate.cartmate.shared.cart.IndividualGroupPurchaseOrigin;
import com.cartmate.cartmate.shared.cart.PaybackFunder;
import com.cartmate.cartmate.shared.cart.PaybackOverlapPolicy;
import com.cartmate.cartmate.shared.cart.dto.PaybackConditionPayload;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaybackConditionEventSerializationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 30, 15);
    private static final List<PaybackConditionPayload> CONDITIONS = List.of(
            new PaybackConditionPayload(9_910_000_101L, PaybackFunder.ADMIN, null, 10, 1_000L),
            new PaybackConditionPayload(9_910_000_101L, PaybackFunder.SELLER, 9_910_000_201L, 5, 300L),
            new PaybackConditionPayload(9_910_000_102L, PaybackFunder.SELLER, 9_910_000_202L, 20, 700L));

    private static ObjectMapper objectMapper;
    private static OutboxEventFactory factory;
    private static EventEnvelopeReader reader;

    @BeforeAll
    static void bootJacksonMapper() {
        AtomicReference<ObjectMapper> mapper = new AtomicReference<>();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(context -> mapper.set(context.getBean(ObjectMapper.class)));
        objectMapper = mapper.get();
        factory = new OutboxEventFactory(objectMapper);
        reader = new EventEnvelopeReader(objectMapper);
    }

    @Test
    @DisplayName("개설 이벤트는 아웃박스 · Kafka 봉투를 거쳐도 겹침 정책 · 조건 목록 · null 판매자 ID가 그대로 복원된다")
    void openedRoundTrip() {
        IndividualGroupPurchaseOpenedEvent event = new IndividualGroupPurchaseOpenedEvent(
                9_910_000_001L, NOW.plusDays(3), List.of(9_910_000_101L, 9_910_000_102L), 2L, NOW,
                PaybackOverlapPolicy.LARGER, CONDITIONS);

        IndividualGroupPurchaseOpenedEvent restored = roundTrip(event, IndividualGroupPurchaseOpenedEvent.class);

        assertThat(restored).isEqualTo(event);
        assertThat(restored.paybackConditions().getFirst().sellerId()).isNull();
        assertThat(restored.paybackConditions()).extracting(PaybackConditionPayload::funder)
                .containsExactly(PaybackFunder.ADMIN, PaybackFunder.SELLER, PaybackFunder.SELLER);
    }

    @Test
    @DisplayName("변경 이벤트는 아웃박스 · Kafka 봉투를 거쳐도 조건 목록이 그대로 복원되고, 빈 목록은 빈 목록으로 남는다")
    void updatedRoundTrip() {
        IndividualGroupPurchaseUpdatedEvent event = new IndividualGroupPurchaseUpdatedEvent(
                9_910_000_002L, NOW.plusDays(2), List.of(9_910_000_101L), 5L, NOW,
                PaybackOverlapPolicy.SELLER_ONLY, CONDITIONS);
        IndividualGroupPurchaseUpdatedEvent empty = new IndividualGroupPurchaseUpdatedEvent(
                9_910_000_003L, NOW.plusDays(2), List.of(9_910_000_101L), 1L, NOW,
                PaybackOverlapPolicy.BOTH, List.of());

        assertThat(roundTrip(event, IndividualGroupPurchaseUpdatedEvent.class)).isEqualTo(event);
        assertThat(roundTrip(empty, IndividualGroupPurchaseUpdatedEvent.class).paybackConditions()).isEmpty();
    }

    @Test
    @DisplayName("종료 이벤트는 아웃박스 · Kafka 봉투를 거쳐도 출처 · 종료 사유 · 조건 목록이 그대로 복원된다")
    void closedRoundTrip() {
        IndividualGroupPurchaseClosedEvent event = new IndividualGroupPurchaseClosedEvent(
                9_910_000_004L, IndividualGroupPurchaseOrigin.ADMIN_DEMAND,
                IndividualGroupPurchaseCloseReason.values()[0], NOW, NOW.plusMinutes(1), 7L,
                PaybackOverlapPolicy.ADMIN_ONLY, CONDITIONS);

        assertThat(roundTrip(event, IndividualGroupPurchaseClosedEvent.class)).isEqualTo(event);
    }

    @Test
    @DisplayName("수요 공동구매 개설 이벤트는 아웃박스 · Kafka 봉투를 거쳐도 페이백 기준 수량 · 개당 금액 · 겹침 정책이 복원된다")
    void demandOpenedRoundTrip() {
        DemandGroupPurchaseOpenedEvent event = new DemandGroupPurchaseOpenedEvent(
                9_910_000_005L, 9_910_000_101L, "수요 공동구매", "invitecode01", NOW.plusDays(3),
                10, 1_000L, PaybackOverlapPolicy.LARGER, NOW);

        assertThat(roundTrip(event, DemandGroupPurchaseOpenedEvent.class)).isEqualTo(event);
    }

    @Test
    @DisplayName("PB-2 이전 형식(paybackMinBuyers · paybackAmount, 조건 필드 없음)의 개설 · 변경 · 종료 JSON은 실패하지 않고 겹침 정책 · 조건 목록이 null이 된다")
    void legacyIndividualJsonWithoutConditions() {
        IndividualGroupPurchaseOpenedEvent opened = new IndividualGroupPurchaseOpenedEvent(
                9_910_000_001L, NOW.plusDays(3), List.of(9_910_000_101L), 2L, NOW,
                PaybackOverlapPolicy.LARGER, CONDITIONS);
        IndividualGroupPurchaseUpdatedEvent updated = new IndividualGroupPurchaseUpdatedEvent(
                9_910_000_002L, NOW.plusDays(2), List.of(9_910_000_101L), 5L, NOW,
                PaybackOverlapPolicy.BOTH, CONDITIONS);
        IndividualGroupPurchaseClosedEvent closed = new IndividualGroupPurchaseClosedEvent(
                9_910_000_004L, IndividualGroupPurchaseOrigin.ADMIN_DEMAND,
                IndividualGroupPurchaseCloseReason.values()[0], NOW, NOW.plusMinutes(1), 7L,
                PaybackOverlapPolicy.BOTH, CONDITIONS);

        IndividualGroupPurchaseOpenedEvent legacyOpened = objectMapper.readValue(
                legacyPayload(opened, true, "paybackOverlapPolicy", "paybackConditions"),
                IndividualGroupPurchaseOpenedEvent.class);
        IndividualGroupPurchaseUpdatedEvent legacyUpdated = objectMapper.readValue(
                legacyPayload(updated, false, "paybackOverlapPolicy", "paybackConditions"),
                IndividualGroupPurchaseUpdatedEvent.class);
        IndividualGroupPurchaseClosedEvent legacyClosed = objectMapper.readValue(
                legacyPayload(closed, true, "paybackOverlapPolicy", "paybackConditions"),
                IndividualGroupPurchaseClosedEvent.class);

        assertThat(legacyOpened.paybackOverlapPolicy()).isNull();
        assertThat(legacyOpened.paybackConditions()).isNull();
        assertThat(legacyOpened.productIds()).containsExactly(9_910_000_101L);
        assertThat(legacyUpdated.paybackOverlapPolicy()).isNull();
        assertThat(legacyUpdated.paybackConditions()).isNull();
        assertThat(legacyClosed.paybackOverlapPolicy()).isNull();
        assertThat(legacyClosed.paybackConditions()).isNull();
        assertThat(legacyClosed.origin()).isEqualTo(IndividualGroupPurchaseOrigin.ADMIN_DEMAND);
    }

    @Test
    @DisplayName("PB-2 이전 형식(paybackMinBuyers · paybackAmount)의 수요 공동구매 개설 JSON은 int 필드 누락으로 역직렬화에 실패한다")
    void legacyDemandJsonFails() {
        DemandGroupPurchaseOpenedEvent event = new DemandGroupPurchaseOpenedEvent(
                9_910_000_005L, 9_910_000_101L, "수요 공동구매", "invitecode01", NOW.plusDays(3),
                10, 1_000L, PaybackOverlapPolicy.LARGER, NOW);
        String legacy = legacyPayload(event, true,
                "paybackMinQuantity", "paybackAmountPerUnit", "paybackOverlapPolicy");

        assertThatThrownBy(() -> objectMapper.readValue(legacy, DemandGroupPurchaseOpenedEvent.class))
                .isInstanceOf(MismatchedInputException.class)
                .hasMessageContaining("paybackMinQuantity");
    }

    @Test
    @DisplayName("옛 JSON에 조건 목록이 null로 들어 있어도 실패하지 않고 null로 역직렬화된다")
    void legacyJsonWithNullConditions() {
        IndividualGroupPurchaseOpenedEvent event = new IndividualGroupPurchaseOpenedEvent(
                9_910_000_001L, NOW.plusDays(3), List.of(9_910_000_101L), 2L, NOW, null, null);

        IndividualGroupPurchaseOpenedEvent restored = roundTrip(event, IndividualGroupPurchaseOpenedEvent.class);

        assertThat(restored.paybackConditions()).isNull();
        assertThat(restored.paybackOverlapPolicy()).isNull();
    }

    private static <E extends DomainEvent> E roundTrip(E event, Class<E> type) {
        OutboxEvent outbox = factory.create(event);
        EventEnvelope sent = new EventEnvelope(outbox.getEventId(), outbox.getEventType(), outbox.getTopic(),
                outbox.getAggregateType(), outbox.getAggregateId(), outbox.getOccurredAt(), outbox.getVersion(),
                outbox.getPayload());
        String kafkaValue = objectMapper.writeValueAsString(sent);

        EventEnvelope received = reader.envelope(kafkaValue);

        assertThat(received).isEqualTo(sent);
        return reader.payload(received, type);
    }

    private static String legacyPayload(DomainEvent event, boolean withOldPaybackFields, String... removedFields) {
        ObjectNode payload = (ObjectNode) objectMapper.readTree(factory.create(event).getPayload());
        for (String field : removedFields) {
            assertThat(payload.has(field)).isTrue();
            payload.remove(field);
        }
        if (withOldPaybackFields) {
            payload.put("paybackMinBuyers", 10);
            payload.put("paybackAmount", 1_000L);
        }
        return objectMapper.writeValueAsString(payload);
    }
}
