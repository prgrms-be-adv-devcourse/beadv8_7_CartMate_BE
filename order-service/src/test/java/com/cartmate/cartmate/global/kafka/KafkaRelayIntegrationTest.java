package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseSignalRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupshared.SharedGroupPurchaseCreateResponse;
import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.outbox.InProcessOutboxRelaySink;
import com.cartmate.cartmate.global.outbox.OutboxEvent;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import com.cartmate.cartmate.global.outbox.OutboxTopics;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseMemberLeftEvent;
import com.cartmate.cartmate.support.CartMateKafkaIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.cartmate.cartmate.support.KafkaTestTopics.awaitRecord;
import static com.cartmate.cartmate.support.KafkaTestTopics.awaitUntil;
import static com.cartmate.cartmate.support.KafkaTestTopics.header;
import static com.cartmate.cartmate.support.OutboxRelayHelper.relayUntilPublished;
import static org.assertj.core.api.Assertions.assertThat;

@CartMateKafkaIntegrationTest
class KafkaRelayIntegrationTest {

    private static final String LEFT_TOPIC = OutboxTopics.of(SharedGroupPurchaseMemberLeftEvent.class);

    @Autowired
    private SharedGroupPurchaseFacade sharedGroupPurchaseFacade;

    @Autowired
    private AbuseSignalRepository abuseSignalRepository;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private InProcessOutboxRelaySink inProcessOutboxRelaySink;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final long base = 9_250_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @Test
    @DisplayName("Kafka를 켜고 아웃박스를 릴레이하면 키 · 헤더 · 봉투 JSON이 계약대로 토픽에 실리고 행은 PUBLISHED가 된다")
    void relaySendsEnvelopeToTopic() {
        long member = nextMemberId();
        SharedGroupPurchaseCreateResponse purchase = create(nextMemberId());
        sharedGroupPurchaseFacade.join(member, purchase.inviteCode());
        sharedGroupPurchaseFacade.leave(member, purchase.id());
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id, event_id, topic, aggregate_type, aggregate_id, version, payload FROM outbox_event "
                        + "WHERE event_type = 'SharedGroupPurchaseMemberLeftEvent' AND aggregate_id = ?",
                String.valueOf(purchase.id()));
        String eventId = (String) row.get("event_id");

        relayUntilPublished(jdbcTemplate, outboxRelay, "SharedGroupPurchase", purchase.id());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM outbox_event WHERE id = ?", String.class, row.get("id")))
                .isEqualTo("PUBLISHED");
        ConsumerRecord<String, String> record = awaitRecord(LEFT_TOPIC, r -> eventId.equals(header(r, "eventId")));
        assertThat(record.key()).isEqualTo(String.valueOf(purchase.id()));
        assertThat(header(record, KafkaOutboxRelaySink.HEADER_EVENT_TYPE)).isEqualTo("SharedGroupPurchaseMemberLeftEvent");
        EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);
        assertThat(envelope.eventId()).isEqualTo(eventId);
        assertThat(envelope.eventType()).isEqualTo("SharedGroupPurchaseMemberLeftEvent");
        assertThat(envelope.topic()).isEqualTo(LEFT_TOPIC).isEqualTo(row.get("topic"));
        assertThat(envelope.aggregateType()).isEqualTo("SharedGroupPurchase");
        assertThat(envelope.aggregateId()).isEqualTo(String.valueOf(purchase.id()));
        assertThat(envelope.version()).isEqualTo(((Number) row.get("version")).intValue());
        assertThat(envelope.payload()).isEqualTo(row.get("payload"));
        assertThat(objectMapper.readValue(envelope.payload(), SharedGroupPurchaseMemberLeftEvent.class).memberId())
                .isEqualTo(member);
    }

    @Test
    @DisplayName("Kafka를 켜면 공동구매 이탈은 Kafka 재전달로 접수 · 판정되고 신호는 하나만 생긴다")
    void leaveReachesAbuseThroughKafka() {
        long member = nextMemberId();
        SharedGroupPurchaseCreateResponse purchase = create(nextMemberId());
        sharedGroupPurchaseFacade.join(member, purchase.inviteCode());

        sharedGroupPurchaseFacade.leave(member, purchase.id());
        relayUntilPublished(jdbcTemplate, outboxRelay, "SharedGroupPurchase", purchase.id());

        awaitUntil(() -> abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member).stream()
                .anyMatch(s -> s.getStatus() == AbuseSignalStatus.JUDGED), "이탈 신호가 판정되지 않았다: member=" + member);
        List<AbuseSignal> signals = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member);
        assertThat(signals).hasSize(1);
        assertThat(signals.getFirst().getSignalType()).isEqualTo(AbuseSignalType.SHARED_PURCHASE_LEFT);
        assertThat(signals.getFirst().getContextKey()).isEqualTo("sharedGroupPurchase:" + purchase.id());
    }

    @Test
    @DisplayName("Kafka를 켜면 서비스 안 전달은 리스너가 무시하고 같은 봉투의 Kafka 재전달만 처리한다")
    void inProcessIgnoredWhenKafkaEnabled() {
        long member = nextMemberId();
        long purchaseId = nextMemberId();
        LocalDateTime leftAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        EventEnvelope envelope = envelope(new SharedGroupPurchaseMemberLeftEvent(purchaseId, member, leftAt.minusHours(1), leftAt));

        inProcessOutboxRelaySink.send(envelope);

        assertThat(abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member)).isEmpty();

        kafkaTemplate.send(LEFT_TOPIC, envelope.aggregateId(), objectMapper.writeValueAsString(envelope));

        awaitUntil(() -> !abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member).isEmpty(),
                "Kafka 재전달로 이탈 신호가 접수되지 않았다: member=" + member);
        assertThat(abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member))
                .singleElement()
                .satisfies(s -> assertThat(s.getSignalType()).isEqualTo(AbuseSignalType.SHARED_PURCHASE_LEFT));
    }

    private EventEnvelope envelope(SharedGroupPurchaseMemberLeftEvent event) {
        return new EventEnvelope(UUID.randomUUID().toString(), SharedGroupPurchaseMemberLeftEvent.class.getSimpleName(),
                LEFT_TOPIC, event.aggregateType(), event.aggregateId(), LocalDateTime.now(), OutboxEvent.SCHEMA_VERSION,
                objectMapper.writeValueAsString(event));
    }

    private SharedGroupPurchaseCreateResponse create(long creator) {
        return sharedGroupPurchaseFacade.create(creator, new SharedGroupPurchaseCreateRequest("Kafka 재전달", 5));
    }

    private long nextMemberId() {
        return base + sequence++;
    }
}
