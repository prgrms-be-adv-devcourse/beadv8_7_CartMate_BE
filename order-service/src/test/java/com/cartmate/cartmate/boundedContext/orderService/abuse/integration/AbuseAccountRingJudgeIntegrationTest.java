package com.cartmate.cartmate.boundedContext.orderService.abuse.integration;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseIdentityCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRuleResult;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseMemberStateRepository;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseSignalRepository;
import com.cartmate.cartmate.support.CartMateIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@CartMateIntegrationTest
class AbuseAccountRingJudgeIntegrationTest {

    @Autowired
    private AbuseFacade abuseFacade;

    @Autowired
    private AbuseSignalRepository abuseSignalRepository;

    @Autowired
    private AbuseMemberStateRepository abuseMemberStateRepository;

    @Test
    @DisplayName("같은 전화를 쓰는 3계정이 공동구매 2곳에서 함께 무산시키면, 무리 점수가 더해져 혼자일 때보다 먼저 제한된다")
    void ringBoostReachesRestriction() {
        List<Long> ring = List.of(1L, 2L, 3L);
        ring.forEach(member -> identity(member, "010-7777-8888"));

        actTogether(ring);

        AbuseSignal last = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(3L).getLast();
        assertThat(last.getRuleResults()).contains(new AbuseRuleResult("ACCOUNT_RING", 2, 68));
        assertThat(last.getTotalScore()).isEqualTo(68 + 68);
        assertThat(level(3L)).isEqualTo(AbuseLevel.RESTRICTED);
    }

    @Test
    @DisplayName("같은 행동이라도 전화 · 주소가 겹치지 않는 계정들은 무리로 묶이지 않아 각자 점수로만 판정된다")
    void strangersAreJudgedAlone() {
        List<Long> strangers = List.of(11L, 12L, 13L);
        strangers.forEach(member -> identity(member, "010-0000-00" + member));

        actTogether(strangers);

        AbuseSignal last = abuseSignalRepository.findByMemberIdOrderByOccurredAtAsc(13L).getLast();
        assertThat(last.getRuleResults()).extracting(AbuseRuleResult::ruleCode).doesNotContain("ACCOUNT_RING");
        assertThat(last.getTotalScore()).isEqualTo(68);
        assertThat(level(13L)).isEqualTo(AbuseLevel.SUSPICIOUS);
    }

    private void actTogether(List<Long> members) {
        for (long purchase : List.of(100L, 200L)) {
            for (Long member : members) {
                abuseFacade.receive(new AbuseSignalCommand(member, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                        "order-" + purchase, LocalDateTime.now(), AbuseSignalCommand.sharedGroupPurchaseContext(purchase)));
                abuseFacade.confirmLinks(member);
                abuseFacade.judgePending(member);
            }
        }
    }

    private void identity(long memberId, String phone) {
        abuseFacade.recordIdentity(new AbuseIdentityCommand(memberId, phone, "00000", "주소 " + memberId, ""));
    }

    private AbuseLevel level(long memberId) {
        return abuseMemberStateRepository.findById(memberId).orElseThrow().getCurrentLevel();
    }
}
