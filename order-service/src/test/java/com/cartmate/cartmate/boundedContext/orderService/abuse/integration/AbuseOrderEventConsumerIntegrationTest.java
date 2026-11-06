package com.cartmate.cartmate.boundedContext.orderService.abuse.integration;

import com.cartmate.cartmate.shared.order.SharedGroupOrderCancelReason;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseSignalRepository;
import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.shared.order.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.address;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.sharedLine;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.sharedOrder;
import static com.cartmate.cartmate.support.OutboxRelayHelper.relayUntilPublished;
import static org.assertj.core.api.Assertions.assertThat;

@CartMateIntegrationTest
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

    private final long base = 9_100_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

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

        relayUntilPublished(jdbcTemplate, outboxRelay, "SharedGroupOrder", orderId);

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

    private SharedGroupOrderCanceledEvent canceled(long orderId, List<Long> unpaidMemberIds) {
        return new SharedGroupOrderCanceledEvent(orderId, orderId, SharedGroupOrderCancelReason.DEADLINE_EXPIRED, LocalDateTime.now(), unpaidMemberIds, null);
    }

    private OutboxMessageRelayed relayed(SharedGroupOrderCanceledEvent event) {
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID().toString(), event.getClass().getSimpleName(),
                "cartmate.order.shared-group-order-canceled", event.aggregateType(), event.aggregateId(),
                event.canceledAt(), 1, "{}");
        return new OutboxMessageRelayed(envelope, event);
    }

    private Long saveExpiredOrder(long creator, long unpaid) {
        long purchaseId = base + sequence++;
        LocalDateTime closedAt = LocalDateTime.now()
                .minus(SharedGroupOrder.PAYMENT_PERIOD)
                .minus(SharedGroupOrder.GRACE_PERIOD)
                .minusMinutes(5);
        return transactionTemplate.execute(status -> {
            SharedGroupOrder order = sharedOrder(purchaseId, creator, unpaid, closedAt, sharedLine(2L, 4L, 1L, 1, 30000L));
            order.markStockReserved();
            order.pay(creator, creator, closedAt.plusMinutes(1), address());
            return sharedGroupOrderRepository.save(order).getId();
        });
    }

    private long nextOrderId() {
        return base + sequence++;
    }

    private long nextMemberId() {
        return base + sequence++;
    }
}
