package com.cartmate.cartmate.boundedContext.abuse.integration;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseSignalRepository;
import com.cartmate.cartmate.boundedContext.abuse.support.AbuseTestDataCleaner;
import com.cartmate.cartmate.boundedContext.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.boundedContext.order.support.TestDataCleaner;
import com.cartmate.cartmate.global.outbox.ManualOutboxRelayConfig;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import com.cartmate.cartmate.shared.event.EventEnvelope;
import com.cartmate.cartmate.shared.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"cartmate.scheduling.enabled=false", "custom.outbox.relay.initial-delay=PT1H"})
@Import(ManualOutboxRelayConfig.class)
@ActiveProfiles("test")
class AbuseOrderEventConsumerIntegrationTest {

    @Autowired
    private SharedGroupOrderFacade sharedGroupOrderFacade;

    @Autowired
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    @Autowired
    private AbuseSignalRepository abuseSignalRepository;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> purchaseIds = new ArrayList<>();
    private final List<String> orderIds = new ArrayList<>();

    private final long base = 9_100_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @AfterEach
    void tearDown() {
        new AbuseTestDataCleaner(jdbcTemplate).deleteRecordsOf(memberIds);
        new TestDataCleaner(jdbcTemplate).deleteSharedGroupOrdersOfPurchases(purchaseIds);
        if (!orderIds.isEmpty()) {
            new NamedParameterJdbcTemplate(jdbcTemplate).update(
                    "DELETE FROM outbox_event WHERE aggregate_type = 'SharedGroupOrder' AND aggregate_id IN (:ids)",
                    Map.of("ids", orderIds));
        }
    }

    @Test
    @DisplayName("기한 만료 취소 트랜잭션은 abuse 테이블을 쓰지 않고, 아웃박스 릴레이 뒤에 미결제 참여자 신호가 접수·판정된다")
    void deadlineCancelReachesAbuseThroughOutbox() {
        long creator = nextMemberId();
        long unpaid = nextMemberId();
        Long orderId = saveExpiredOrder(creator, unpaid);

        sharedGroupOrderFacade.cancelByDeadline(orderId);

        assertThat(sharedGroupOrderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(SharedGroupOrderStatus.CANCELED);
        assertThat(abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(unpaid)).isEmpty();

        relayUntilPublished(orderId);

        assertThat(abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(creator)).isEmpty();
        List<AbuseSignal> signals = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(unpaid);
        assertThat(signals).hasSize(1);
        AbuseSignal signal = signals.getFirst();
        assertThat(signal.getSignalType()).isEqualTo(AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED);
        assertThat(signal.getSourceKey()).isEqualTo(String.valueOf(orderId));
        assertThat(signal.getOccurredAt()).isEqualTo(sharedGroupOrderRepository.findById(orderId).orElseThrow().getCanceledAt());
        assertThat(signal.getStatus()).isEqualTo(AbuseSignalStatus.JUDGED);
        assertThat(signal.getLevel()).isEqualTo(AbuseLevel.CAUTION);
    }

    @Test
    @DisplayName("같은 메시지가 두 번 전달돼도 신호는 한 건만 남고 한 번만 판정된다")
    void duplicateDeliveryIsIdempotent() {
        long member = nextMemberId();
        OutboxMessageRelayed message = relayed(canceled(nextOrderId(), Arrays.asList(member)));

        eventPublisher.publishEvent(message);
        eventPublisher.publishEvent(message);

        List<AbuseSignal> signals = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member);
        assertThat(signals).hasSize(1);
        assertThat(signals.getFirst().getStatus()).isEqualTo(AbuseSignalStatus.JUDGED);
        assertThat(signals.getFirst().getLevel()).isEqualTo(AbuseLevel.CAUTION);
    }

    @Test
    @DisplayName("미결제자 목록의 잘못된 항목은 건너뛰고 나머지 회원 신호는 접수된다")
    void invalidMemberIsSkipped() {
        long member = nextMemberId();
        long orderId = nextOrderId();

        eventPublisher.publishEvent(relayed(canceled(orderId, Arrays.asList(null, member))));

        assertThat(abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member))
                .extracting(AbuseSignal::getSourceKey)
                .containsExactly(String.valueOf(orderId));
    }

    private void relayUntilPublished(Long orderId) {
        for (int i = 0; i < 20; i++) {
            ManualOutboxRelayConfig.releaseRelayLock(jdbcTemplate);
            outboxRelay.relay();
            Integer pending = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbox_event WHERE aggregate_type = 'SharedGroupOrder' AND aggregate_id = ? AND status = 'NEW'",
                    Integer.class, String.valueOf(orderId));
            if (pending == 0) {
                return;
            }
        }
        throw new AssertionError("아웃박스 이벤트가 릴레이되지 않았다: sharedGroupOrderId=" + orderId);
    }

    private SharedGroupOrderCanceledEvent canceled(long orderId, List<Long> unpaidMemberIds) {
        return new SharedGroupOrderCanceledEvent(orderId, orderId, "DEADLINE_EXPIRED", LocalDateTime.now(), unpaidMemberIds);
    }

    private OutboxMessageRelayed relayed(SharedGroupOrderCanceledEvent event) {
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID().toString(), event.getClass().getSimpleName(),
                "cartmate.order.shared-group-order-canceled", event.aggregateType(), event.aggregateId(),
                event.canceledAt(), 1, "{}");
        return new OutboxMessageRelayed(envelope, event);
    }

    private Long saveExpiredOrder(long creator, long unpaid) {
        long purchaseId = base + sequence++;
        purchaseIds.add(purchaseId);
        LocalDateTime closedAt = LocalDateTime.now()
                .minus(SharedGroupOrder.PAYMENT_PERIOD)
                .minus(SharedGroupOrder.GRACE_PERIOD)
                .minusMinutes(5);
        Long orderId = transactionTemplate.execute(status -> {
            SharedGroupOrder order = SharedGroupOrder.create(purchaseId, creator, List.of(creator, unpaid),
                    List.of(new OrderItemLine(2L, 4L, 1L, "상품", null, 1, 30000L)), closedAt);
            order.markStockReserved();
            order.pay(creator, creator, closedAt.plusMinutes(1),
                    DeliveryAddress.of("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"));
            return sharedGroupOrderRepository.save(order).getId();
        });
        orderIds.add(String.valueOf(orderId));
        return orderId;
    }

    private long nextOrderId() {
        return base + sequence++;
    }

    private long nextMemberId() {
        long memberId = base + sequence++;
        memberIds.add(memberId);
        return memberId;
    }
}
