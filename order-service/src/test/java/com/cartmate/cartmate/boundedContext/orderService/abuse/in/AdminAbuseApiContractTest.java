package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseIdentityCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.support.ExternalApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAbuseApiContractTest extends ExternalApiTestSupport {

    private static final long ADMIN = 900_000_001L;

    @Autowired
    private AbuseFacade abuseFacade;

    @Test
    @DisplayName("회원 사례에 단계 · 제한 · 최근 점수 구성(무리 가산 포함) · 신호 · 제한 이력 · 연결 · 무리가 정해진 필드로 나온다")
    void memberCase() throws Exception {
        List<Long> ring = List.of(1L, 2L, 3L);
        ring.forEach(member -> abuseFacade.recordIdentity(
                new AbuseIdentityCommand(member, "010-4444-5555", "00000", "주소 " + member, "1호")));
        for (long purchase : List.of(100L, 200L)) {
            ring.forEach(member -> expired(member, purchase));
        }

        ok(get("/api/admin/abuse/members/3").with(admin(ADMIN)))
                .andExpect(jsonPath("$.data.memberId").value(3))
                .andExpect(jsonPath("$.data.currentLevel").value("RESTRICTED"))
                .andExpect(jsonPath("$.data.restricted").value(true))
                .andExpect(jsonPath("$.data.restrictedUntil").value(matchesPattern(ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data.latestTotalScore").value(136))
                .andExpect(jsonPath("$.data.latestRuleScores[?(@.ruleCode == 'ACCOUNT_RING')].score", contains(68)))
                .andExpect(jsonPath("$.data.latestRuleScores[?(@.ruleCode == 'ACCOUNT_RING')].signalCount", contains(2)))
                .andExpect(jsonPath("$.data.signals", hasSize(2)))
                .andExpect(jsonPath("$.data.signals[0].signalType").value("SHARED_ORDER_PAYMENT_EXPIRED"))
                .andExpect(jsonPath("$.data.signals[0].contextKey").value("sharedGroupPurchase:200"))
                .andExpect(jsonPath("$.data.signals[0].status").value("JUDGED"))
                .andExpect(jsonPath("$.data.restrictions", hasSize(1)))
                .andExpect(jsonPath("$.data.restrictions[0].active").value(true))
                .andExpect(jsonPath("$.data.links[*].memberId", containsInAnyOrder(1, 2)))
                .andExpect(jsonPath("$.data.links[*].identityType", contains("PHONE", "PHONE")))
                .andExpect(jsonPath("$.data.links[*].sharedContextCount", contains(2, 2)))
                .andExpect(jsonPath("$.data.ring[*].memberId", containsInAnyOrder(1, 2)));
    }

    @Test
    @DisplayName("어뷰징 기록이 없는 회원은 404가 아니라 NONE과 빈 목록으로 응답한다")
    void memberWithoutRecord() throws Exception {
        ok(get("/api/admin/abuse/members/999").with(admin(ADMIN)))
                .andExpect(jsonPath("$.data.currentLevel").value("NONE"))
                .andExpect(jsonPath("$.data.restricted").value(false))
                .andExpect(jsonPath("$.data.signals", hasSize(0)))
                .andExpect(jsonPath("$.data.ring", hasSize(0)));
    }

    @Test
    @DisplayName("단계별 회원 목록은 minLevel 이상만, 마지막 판정이 최근인 순으로 limit만큼 주고 단계가 없는 회원은 빠진다")
    void flaggedMembers() throws Exception {
        abuseFacade.recordIdentity(new AbuseIdentityCommand(9L, "010-1212-3434", "00000", "주소 9", "1호"));
        expired(10L, 1L);
        expired(11L, 2L);
        expired(11L, 3L);
        expired(12L, 4L);
        expired(12L, 5L);

        ok(get("/api/admin/abuse/members").param("minLevel", "SUSPICIOUS").with(admin(ADMIN)))
                .andExpect(jsonPath("$.data[*].memberId", contains(12, 11)))
                .andExpect(jsonPath("$.data[*].currentLevel", contains("SUSPICIOUS", "SUSPICIOUS")))
                .andExpect(jsonPath("$.data[0].restricted").value(false));
        ok(get("/api/admin/abuse/members").with(admin(ADMIN)))
                .andExpect(jsonPath("$.data[*].memberId", contains(12, 11, 10)));
        ok(get("/api/admin/abuse/members").param("limit", "1").with(admin(ADMIN)))
                .andExpect(jsonPath("$.data[*].memberId", contains(12)));
    }

    @Test
    @DisplayName("없는 단계 값을 주면 400과 단계 값 오류 응답을 준다")
    void invalidLevel() throws Exception {
        mvc.perform(get("/api/admin/abuse/members").param("minLevel", "HIGH").with(admin(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-ABUSE_INVALID_LEVEL"));
    }

    private void expired(long memberId, long sharedGroupPurchaseId) {
        abuseFacade.receive(new AbuseSignalCommand(memberId, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                "order-" + sharedGroupPurchaseId, LocalDateTime.now(),
                AbuseSignalCommand.sharedGroupPurchaseContext(sharedGroupPurchaseId)));
        abuseFacade.confirmLinks(memberId);
        abuseFacade.judgePending(memberId);
    }
}
