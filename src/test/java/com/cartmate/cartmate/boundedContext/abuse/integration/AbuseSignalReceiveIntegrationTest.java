package com.cartmate.cartmate.boundedContext.abuse.integration;

import com.cartmate.cartmate.boundedContext.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseSignalRepository;
import com.cartmate.cartmate.boundedContext.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.boundedContext.order.support.TestDataCleaner;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class AbuseSignalReceiveIntegrationTest {

    @Autowired
    private SharedGroupOrderFacade sharedGroupOrderFacade;

    @Autowired
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    @Autowired
    private AbuseFacade abuseFacade;

    @Autowired
    private AbuseSignalRepository abuseSignalRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> purchaseIds = new ArrayList<>();

    private final long base = 9_100_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @AfterEach
    void tearDown() {
        TestDataCleaner cleaner = new TestDataCleaner(jdbcTemplate);
        cleaner.deleteAbuseRecordsOf(memberIds);
        cleaner.deleteSharedGroupOrdersOfPurchases(purchaseIds);
    }

    @Test
    @DisplayName("공동 주문이 기한 만료로 취소되면 같은 트랜잭션에서 미결제 참여자 신호만 RECEIVED로 저장된다")
    void deadlineCancelReceivesSignalForUnpaidMember() {
        long creator = nextMemberId();
        long unpaid = nextMemberId();
        Long orderId = saveExpiredOrder(creator, unpaid);

        sharedGroupOrderFacade.cancelByDeadline(orderId);

        assertThat(sharedGroupOrderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(SharedGroupOrderStatus.CANCELED);
        assertThat(abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(creator)).isEmpty();
        List<AbuseSignal> signals = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(unpaid);
        assertThat(signals).hasSize(1);
        AbuseSignal signal = signals.getFirst();
        assertThat(signal.getSignalType()).isEqualTo(AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED);
        assertThat(signal.getSourceKey()).isEqualTo(String.valueOf(orderId));
        assertThat(signal.getStatus()).isEqualTo(AbuseSignalStatus.RECEIVED);
        assertThat(signal.getOccurredAt()).isEqualTo(canceledAtOf(orderId));
        assertThat(signal.getReceivedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 신호를 두 번 접수해도 한 행만 남는다")
    void duplicateSignalIsStoredOnce() {
        long member = nextMemberId();
        AbuseSignalCommand command = command(member, "dup-" + member);

        abuseFacade.receive(command);
        abuseFacade.receive(command);

        assertThat(abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member)).hasSize(1);
    }

    @Test
    @DisplayName("접수한 트랜잭션이 롤백되면 신호도 남지 않는다")
    void signalRollsBackWithCallerTransaction() {
        long member = nextMemberId();

        transactionTemplate.executeWithoutResult(status -> {
            abuseFacade.receive(command(member, "rollback-" + member));
            status.setRollbackOnly();
        });

        assertThat(abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member)).isEmpty();
    }

    @Test
    @DisplayName("미결제자 목록의 잘못된 항목은 건너뛰고 나머지 신호는 저장된다")
    void invalidMemberIsSkipped() {
        long member = nextMemberId();
        long orderId = base + sequence++;

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
                new SharedGroupOrderCanceledEvent(orderId, orderId, "DEADLINE_EXPIRED", LocalDateTime.now(),
                        Arrays.asList(null, member))));

        assertThat(abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member))
                .extracting(AbuseSignal::getSourceKey)
                .containsExactly(String.valueOf(orderId));
    }

    private AbuseSignalCommand command(long memberId, String sourceKey) {
        return new AbuseSignalCommand(memberId, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED, sourceKey, LocalDateTime.now());
    }

    private LocalDateTime canceledAtOf(Long orderId) {
        return sharedGroupOrderRepository.findById(orderId).orElseThrow().getCanceledAt();
    }

    private Long saveExpiredOrder(long creator, long unpaid) {
        long purchaseId = base + sequence++;
        purchaseIds.add(purchaseId);
        LocalDateTime closedAt = LocalDateTime.now()
                .minus(SharedGroupOrder.PAYMENT_PERIOD)
                .minus(SharedGroupOrder.GRACE_PERIOD)
                .minusMinutes(5);
        return transactionTemplate.execute(status -> {
            SharedGroupOrder order = SharedGroupOrder.create(purchaseId, creator, List.of(creator, unpaid),
                    List.of(new OrderItemLine(2L, 4L, 1L, "상품", null, 1, 30000L)), closedAt);
            order.markStockReserved();
            order.pay(creator, creator, closedAt.plusMinutes(1),
                    DeliveryAddress.of("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"));
            return sharedGroupOrderRepository.save(order).getId();
        });
    }

    private long nextMemberId() {
        long memberId = base + sequence++;
        memberIds.add(memberId);
        return memberId;
    }
}
