package com.cartmate.cartmate.boundedContext.orderService.abuse.integration;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseSignalRepository;
import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupshared.SharedGroupPurchaseCreateResponse;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.cartmate.cartmate.support.OutboxRelayHelper.relayUntilPublished;
import static org.assertj.core.api.Assertions.assertThat;

@CartMateIntegrationTest
class AbuseCartEventConsumerIntegrationTest {

    @Autowired
    private SharedGroupPurchaseFacade sharedGroupPurchaseFacade;

    @Autowired
    private AbuseSignalRepository abuseSignalRepository;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final long base = 9_150_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @Test
    @DisplayName("공동구매에서 스스로 이탈하면 아웃박스 릴레이 뒤에 이탈 신호가 접수 · 판정된다")
    void leaveReachesAbuseThroughOutbox() {
        long creator = nextMemberId();
        long member = nextMemberId();
        SharedGroupPurchaseCreateResponse purchase = create(creator);
        sharedGroupPurchaseFacade.join(member, purchase.inviteCode());

        sharedGroupPurchaseFacade.leave(member, purchase.id());
        relayUntilPublished(jdbcTemplate, outboxRelay, "SharedGroupPurchase", purchase.id());

        AbuseSignal signal = onlySignal(member);
        assertThat(signal.getSignalType()).isEqualTo(AbuseSignalType.SHARED_PURCHASE_LEFT);
        assertThat(signal.getSourceKey()).startsWith(purchase.id() + ":");
        assertThat(signal.getContextKey()).isEqualTo("sharedGroupPurchase:" + purchase.id());
        assertThat(signal.getStatus()).isEqualTo(AbuseSignalStatus.JUDGED);
        assertThat(signal.getTotalScore()).isEqualTo(13);
    }

    @Test
    @DisplayName("방장이 공동구매를 취소하면 방장에게만 방장 취소 신호가 접수된다")
    void creatorCancelReachesAbuse() {
        long creator = nextMemberId();
        long member = nextMemberId();
        SharedGroupPurchaseCreateResponse purchase = create(creator);
        sharedGroupPurchaseFacade.join(member, purchase.inviteCode());

        sharedGroupPurchaseFacade.cancel(creator, purchase.id());
        relayUntilPublished(jdbcTemplate, outboxRelay, "SharedGroupPurchase", purchase.id());

        AbuseSignal signal = onlySignal(creator);
        assertThat(signal.getSignalType()).isEqualTo(AbuseSignalType.SHARED_PURCHASE_CREATOR_CANCELED);
        assertThat(signal.getSourceKey()).isEqualTo("purchase:" + purchase.id());
        assertThat(abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(member)).isEmpty();
    }

    @Test
    @DisplayName("진행 중인 공동구매가 5개째가 되는 참여부터 동시 다중 참여 신호가 접수된다")
    void concurrentJoinFromFifth() {
        long creator = nextMemberId();
        long member = nextMemberId();
        List<SharedGroupPurchaseCreateResponse> purchases = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            SharedGroupPurchaseCreateResponse purchase = create(creator);
            purchases.add(purchase);
            sharedGroupPurchaseFacade.join(member, purchase.inviteCode());
        }
        purchases.forEach(p -> relayUntilPublished(jdbcTemplate, outboxRelay, "SharedGroupPurchase", p.id()));

        AbuseSignal signal = onlySignal(member);
        assertThat(signal.getSignalType()).isEqualTo(AbuseSignalType.SHARED_PURCHASE_CONCURRENT_JOIN);
        assertThat(signal.getSourceKey()).startsWith(purchases.getLast().id() + ":");
    }

    private AbuseSignal onlySignal(long memberId) {
        List<AbuseSignal> signals = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(memberId);
        assertThat(signals).hasSize(1);
        return signals.getFirst();
    }

    private SharedGroupPurchaseCreateResponse create(long creator) {
        return sharedGroupPurchaseFacade.create(creator, new SharedGroupPurchaseCreateRequest("어뷰징 신호", 5));
    }

    private long nextMemberId() {
        return base + sequence++;
    }
}
