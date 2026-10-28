package com.cartmate.cartmate.boundedContext.abuse.integration;

import com.cartmate.cartmate.boundedContext.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseJudgment;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRestriction;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRuleResult;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseJudgmentRepository;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseRestrictionRepository;
import com.cartmate.cartmate.boundedContext.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.boundedContext.order.support.TestDataCleaner;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
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
class AbuseSignalIntegrationTest {

    @Autowired
    private SharedGroupOrderFacade sharedGroupOrderFacade;

    @Autowired
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    @Autowired
    private AbuseFacade abuseFacade;

    @Autowired
    private AbuseJudgmentRepository abuseJudgmentRepository;

    @Autowired
    private AbuseRestrictionRepository abuseRestrictionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> purchaseIds = new ArrayList<>();

    private final long base = 9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @AfterEach
    void tearDown() {
        TestDataCleaner cleaner = new TestDataCleaner(jdbcTemplate);
        cleaner.deleteAbuseRecordsOf(memberIds);
        cleaner.deleteSharedGroupOrdersOfPurchases(purchaseIds);
    }

    @Test
    @DisplayName("공동 주문이 기한 만료로 무산되면 커밋 후 미결제 참여자만 1단계 판정을 받고 규칙 결과가 저장된다")
    void deadlineCancelCreatesJudgmentForUnpaidMember() {
        long creator = nextMemberId();
        long unpaid = nextMemberId();
        Long orderId = saveExpiredOrder(creator, unpaid);

        sharedGroupOrderFacade.cancelByDeadline(orderId);

        assertThat(abuseJudgmentRepository.findByMemberIdOrderByJudgedAtAsc(creator)).isEmpty();
        List<AbuseJudgment> judgments = abuseJudgmentRepository.findByMemberIdOrderByJudgedAtAsc(unpaid);
        assertThat(judgments).hasSize(1);
        AbuseJudgment judgment = judgments.getFirst();
        assertThat(judgment.getSignalType()).isEqualTo(AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED);
        assertThat(judgment.getSourceKey()).isEqualTo(String.valueOf(orderId));
        assertThat(judgment.getLevel()).isEqualTo(AbuseLevel.CAUTION);
        assertThat(judgment.getTotalScore()).isEqualTo(40);
        assertThat(judgment.getRuleResults()).containsExactly(
                new AbuseRuleResult("SHARED_ORDER_PAYMENT_EXPIRED", 1, AbuseLevel.CAUTION, 40));
        assertThat(activeRestriction(unpaid)).isNull();
    }

    @Test
    @DisplayName("30일 안 세 번째 무산이면 3단계로 24시간 제한하고, 같은 이벤트가 다시 와도 판정이 늘지 않으며, 네 번째 무산은 제한을 늘린다")
    void thirdExpiryRestrictsAndDuplicateIsIgnored() {
        long member = nextMemberId();
        LocalDateTime now = LocalDateTime.now();

        abuseFacade.handleSharedGroupOrderCanceled(expiredEvent(member, now.minusDays(20)), member);
        abuseFacade.handleSharedGroupOrderCanceled(expiredEvent(member, now.minusDays(10)), member);
        SharedGroupOrderCanceledEvent third = expiredEvent(member, now);
        abuseFacade.handleSharedGroupOrderCanceled(third, member);
        abuseFacade.handleSharedGroupOrderCanceled(third, member);

        List<AbuseJudgment> judgments = abuseJudgmentRepository.findByMemberIdOrderByJudgedAtAsc(member);
        assertThat(judgments).extracting(AbuseJudgment::getLevel)
                .containsExactly(AbuseLevel.CAUTION, AbuseLevel.SUSPICIOUS, AbuseLevel.RESTRICTED);
        AbuseRestriction restriction = activeRestriction(member);
        assertThat(restriction).isNotNull();
        assertThat(restriction.getJudgmentId()).isEqualTo(judgments.getLast().getId());
        assertThat(restriction.getEndsAt()).isEqualTo(judgments.getLast().getJudgedAt().plus(AbuseRestriction.RESTRICTION_PERIOD));

        abuseFacade.handleSharedGroupOrderCanceled(expiredEvent(member, LocalDateTime.now()), member);

        List<AbuseJudgment> after = abuseJudgmentRepository.findByMemberIdOrderByJudgedAtAsc(member);
        assertThat(after).hasSize(4);
        AbuseRestriction extended = activeRestriction(member);
        assertThat(extended.getId()).isEqualTo(restriction.getId());
        assertThat(extended.getJudgmentId()).isEqualTo(after.getLast().getId());
        assertThat(extended.getEndsAt()).isEqualTo(after.getLast().getJudgedAt().plus(AbuseRestriction.RESTRICTION_PERIOD));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM abuse_restriction WHERE member_id = ?", Long.class, member)).isEqualTo(1L);
    }

    private AbuseRestriction activeRestriction(long memberId) {
        return abuseRestrictionRepository
                .findFirstByMemberIdAndReleasedAtIsNullAndEndsAtAfterOrderByEndsAtDesc(memberId, LocalDateTime.now())
                .orElse(null);
    }

    private SharedGroupOrderCanceledEvent expiredEvent(long memberId, LocalDateTime canceledAt) {
        long orderId = base + sequence++;
        return new SharedGroupOrderCanceledEvent(orderId, orderId, "DEADLINE_EXPIRED", canceledAt, List.of(memberId));
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
