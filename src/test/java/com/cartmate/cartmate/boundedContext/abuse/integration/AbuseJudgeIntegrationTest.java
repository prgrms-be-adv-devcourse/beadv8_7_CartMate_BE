package com.cartmate.cartmate.boundedContext.abuse.integration;

import com.cartmate.cartmate.boundedContext.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseMemberState;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRestriction;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRuleResult;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseMemberStateRepository;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseRestrictionRepository;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseSignalRepository;
import com.cartmate.cartmate.boundedContext.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.boundedContext.order.support.TestDataCleaner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class AbuseJudgeIntegrationTest {

    @Autowired
    private SharedGroupOrderFacade sharedGroupOrderFacade;

    @Autowired
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    @Autowired
    private AbuseFacade abuseFacade;

    @Autowired
    private AbuseSignalRepository abuseSignalRepository;

    @Autowired
    private AbuseMemberStateRepository abuseMemberStateRepository;

    @Autowired
    private AbuseRestrictionRepository abuseRestrictionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> purchaseIds = new ArrayList<>();

    private final long base = 9_300_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @AfterEach
    void tearDown() {
        TestDataCleaner cleaner = new TestDataCleaner(jdbcTemplate);
        cleaner.deleteAbuseRecordsOf(memberIds);
        cleaner.deleteSharedGroupOrdersOfPurchases(purchaseIds);
    }

    @Test
    @DisplayName("공동 주문이 기한 만료로 무산되면 커밋 후 미결제 참여자 신호가 1단계 40점으로 판정된다")
    void deadlineCancelJudgesUnpaidMember() {
        long creator = nextMemberId();
        long unpaid = nextMemberId();
        Long orderId = saveExpiredOrder(creator, unpaid);

        sharedGroupOrderFacade.cancelByDeadline(orderId);

        assertThat(abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(creator)).isEmpty();
        AbuseSignal signal = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(unpaid).getFirst();
        assertThat(signal.getStatus()).isEqualTo(AbuseSignalStatus.JUDGED);
        assertThat(signal.getLevel()).isEqualTo(AbuseLevel.CAUTION);
        assertThat(signal.getTotalScore()).isEqualTo(40);
        assertThat(signal.getRuleResults()).containsExactly(
                new AbuseRuleResult("SHARED_ORDER_PAYMENT_EXPIRED", 1, AbuseLevel.CAUTION, 40));
        assertThat(signal.getJudgedAt()).isNotNull();
        AbuseMemberState state = memberState(unpaid);
        assertThat(state.getCurrentLevel()).isEqualTo(AbuseLevel.CAUTION);
        assertThat(state.getRestrictedUntil()).isNull();
        assertThat(abuseRestrictionRepository.findByMemberIdOrderByStartedAtAsc(unpaid)).isEmpty();
    }

    @Test
    @DisplayName("30일 안 세 번째 무산이면 24시간 제한하고, 같은 신호는 다시 판정하지 않으며, 네 번째 무산은 같은 제한을 늘린다")
    void thirdSignalRestrictsAndFourthExtends() {
        long member = nextMemberId();
        LocalDateTime now = LocalDateTime.now();

        receiveAndJudge(member, "first", now.minusDays(20));
        receiveAndJudge(member, "second", now.minusDays(10));
        receiveAndJudge(member, "third", now);

        assertThat(abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member))
                .extracting(AbuseSignal::getLevel)
                .containsExactly(AbuseLevel.CAUTION, AbuseLevel.SUSPICIOUS, AbuseLevel.RESTRICTED);
        AbuseRestriction restriction = onlyRestriction(member);
        AbuseSignal third = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member).getLast();
        assertThat(restriction.getLastSignalId()).isEqualTo(third.getId());
        assertThat(restriction.getEndsAt()).isEqualTo(third.getJudgedAt().plus(AbuseRestriction.RESTRICTION_PERIOD));
        assertThat(memberState(member).getRestrictedUntil()).isEqualTo(restriction.getEndsAt());

        abuseFacade.receive(command(member, "third", now));
        assertThat(abuseFacade.judgePending(member)).isZero();

        receiveAndJudge(member, "fourth", LocalDateTime.now());

        AbuseRestriction extended = onlyRestriction(member);
        AbuseSignal fourth = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member).getLast();
        assertThat(extended.getId()).isEqualTo(restriction.getId());
        assertThat(extended.getExtendedCount()).isEqualTo(1);
        assertThat(extended.getLastSignalId()).isEqualTo(fourth.getId());
        assertThat(extended.getEndsAt()).isEqualTo(fourth.getJudgedAt().plus(AbuseRestriction.RESTRICTION_PERIOD));
        assertThat(memberState(member).getRestrictedUntil()).isEqualTo(extended.getEndsAt());
    }

    @Test
    @DisplayName("제한이 끝난 뒤 다시 3단계가 되면 기존 이력을 늘리지 않고 새 제한을 만든다")
    void newRestrictionAfterPreviousEnded() {
        long member = nextMemberId();
        LocalDateTime now = LocalDateTime.now();
        receiveAndJudge(member, "first", now.minusDays(3));
        receiveAndJudge(member, "second", now.minusDays(2));
        receiveAndJudge(member, "third", now.minusDays(1));
        AbuseRestriction previous = onlyRestriction(member);
        jdbcTemplate.update("UPDATE abuse_member_state SET restricted_until = ? WHERE member_id = ?",
                now.minusMinutes(1), member);

        receiveAndJudge(member, "fourth", LocalDateTime.now());

        List<AbuseRestriction> restrictions = abuseRestrictionRepository.findByMemberIdOrderByStartedAtAsc(member);
        assertThat(restrictions).hasSize(2);
        assertThat(restrictions.getFirst().getId()).isEqualTo(previous.getId());
        assertThat(restrictions.getFirst().getExtendedCount()).isZero();
        AbuseRestriction latest = restrictions.getLast();
        AbuseMemberState state = memberState(member);
        assertThat(state.getActiveRestrictionId()).isEqualTo(latest.getId());
        assertThat(state.getRestrictedUntil()).isEqualTo(latest.getEndsAt());
    }

    @Test
    @DisplayName("30일보다 오래된 신호는 횟수에 들어가지 않는다")
    void signalOutsideWindowIsNotCounted() {
        long member = nextMemberId();

        receiveAndJudge(member, "old", LocalDateTime.now().minusDays(31));

        AbuseSignal signal = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member).getFirst();
        assertThat(signal.getStatus()).isEqualTo(AbuseSignalStatus.JUDGED);
        assertThat(signal.getLevel()).isEqualTo(AbuseLevel.NONE);
        assertThat(signal.getTotalScore()).isZero();
        assertThat(memberState(member).getCurrentLevel()).isEqualTo(AbuseLevel.NONE);
    }

    private void receiveAndJudge(long memberId, String sourceKey, LocalDateTime occurredAt) {
        abuseFacade.receive(command(memberId, sourceKey, occurredAt));
        assertThat(abuseFacade.judgePending(memberId)).isEqualTo(1);
    }

    private AbuseSignalCommand command(long memberId, String sourceKey, LocalDateTime occurredAt) {
        return new AbuseSignalCommand(memberId, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                sourceKey + "-" + memberId, occurredAt);
    }

    private AbuseRestriction onlyRestriction(long memberId) {
        List<AbuseRestriction> restrictions = abuseRestrictionRepository.findByMemberIdOrderByStartedAtAsc(memberId);
        assertThat(restrictions).hasSize(1);
        return restrictions.getFirst();
    }

    private AbuseMemberState memberState(long memberId) {
        return abuseMemberStateRepository.findById(memberId).orElseThrow();
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
