package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.shared.abuse.dto.AbuseRestrictionStatus;
import com.cartmate.cartmate.support.ExternalApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAbuseActionApiTest extends ExternalApiTestSupport {

    private static final long ADMIN = 900_000_002L;
    private static final long MEMBER = 7L;

    @Autowired
    private AbuseFacade abuseFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("수동 제한하면 바로 이용이 막히고, 사례에 수동 제한과 처리자 · 사유가 남는다")
    void restrictManually() throws Exception {
        ok(json(post("/api/admin/abuse/members/7/restrictions"), "{\"hours\":48,\"reason\":\"신고 다수\"}").with(admin(ADMIN)));

        AbuseRestrictionStatus restriction = abuseFacade.getRestriction(MEMBER);
        assertThat(restriction.restricted()).isTrue();
        assertThat(restriction.restrictedUntil()).isAfter(LocalDateTime.now().plusHours(47));
        ok(get("/api/admin/abuse/members/7").with(admin(ADMIN)))
                .andExpect(jsonPath("$.data.currentLevel").value("RESTRICTED"))
                .andExpect(jsonPath("$.data.restrictions[0].source").value("MANUAL"))
                .andExpect(jsonPath("$.data.adminActions[0].actionType").value("RESTRICT"))
                .andExpect(jsonPath("$.data.adminActions[0].adminMemberId").value(num(ADMIN)))
                .andExpect(jsonPath("$.data.adminActions[0].reason").value("신고 다수"));
    }

    @Test
    @DisplayName("수동 제한 중 자동 판정은 제한을 줄이지 않고, 점수가 낮아도 단계는 제한으로 남는다")
    void autoJudgmentDoesNotShortenManualRestriction() throws Exception {
        ok(json(post("/api/admin/abuse/members/7/restrictions"), "{\"hours\":72,\"reason\":\"신고\"}").with(admin(ADMIN)));
        LocalDateTime manualEnd = abuseFacade.getRestriction(MEMBER).restrictedUntil();

        for (long purchase = 1; purchase <= 3; purchase++) {
            expired(MEMBER, purchase);
        }

        assertThat(abuseFacade.getRestriction(MEMBER).restrictedUntil()).isEqualTo(manualEnd);
        ok(get("/api/admin/abuse/members/7").with(admin(ADMIN)))
                .andExpect(jsonPath("$.data.currentLevel").value("RESTRICTED"))
                .andExpect(jsonPath("$.data.restrictions.length()").value(1));
    }

    @Test
    @DisplayName("이미 더 긴 제한이 있으면 더 짧은 수동 제한은 종료를 앞당기지 않는다")
    void shorterManualRestrictionDoesNotShorten() throws Exception {
        ok(json(post("/api/admin/abuse/members/7/restrictions"), "{\"hours\":72,\"reason\":\"1차\"}").with(admin(ADMIN)));
        LocalDateTime longEnd = abuseFacade.getRestriction(MEMBER).restrictedUntil();

        ok(json(post("/api/admin/abuse/members/7/restrictions"), "{\"hours\":24,\"reason\":\"2차\"}").with(admin(ADMIN)));

        assertThat(abuseFacade.getRestriction(MEMBER).restrictedUntil()).isEqualTo(longEnd);
        ok(get("/api/admin/abuse/members/7").with(admin(ADMIN)))
                .andExpect(jsonPath("$.data.restrictions.length()").value(1))
                .andExpect(jsonPath("$.data.restrictions[0].extendedCount").value(0))
                .andExpect(jsonPath("$.data.adminActions.length()").value(2));
    }

    @Test
    @DisplayName("해제하면 바로 이용할 수 있고, 해제 이전 신호는 더 세지 않아 새 신호 하나로는 다시 제한되지 않는다")
    void releaseResetsScoreBaseline() throws Exception {
        for (long purchase = 1; purchase <= 3; purchase++) {
            expired(MEMBER, purchase);
        }
        assertThat(abuseFacade.getRestriction(MEMBER).restricted()).isTrue();

        ok(json(post("/api/admin/abuse/members/7/release"), "{\"reason\":\"오탐 확인\"}").with(admin(ADMIN)));
        assertThat(abuseFacade.getRestriction(MEMBER).restricted()).isFalse();

        expired(MEMBER, 4L);

        assertThat(abuseFacade.getRestriction(MEMBER).restricted()).isFalse();
        ok(get("/api/admin/abuse/members/7").with(admin(ADMIN)))
                .andExpect(jsonPath("$.data.currentLevel").value("CAUTION"))
                .andExpect(jsonPath("$.data.latestTotalScore").value(34))
                .andExpect(jsonPath("$.data.scoreResetAt").isNotEmpty())
                .andExpect(jsonPath("$.data.restrictions[0].releasedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.restrictions[0].active").value(false))
                .andExpect(jsonPath("$.data.adminActions[0].actionType").value("RELEASE"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "{\"hours\":0,\"reason\":\"사유\"}|400-ABUSE_INVALID_RESTRICTION_HOURS",
            "{\"hours\":721,\"reason\":\"사유\"}|400-ABUSE_INVALID_RESTRICTION_HOURS",
            "{\"hours\":24,\"reason\":\"  \"}|400-ABUSE_ADMIN_REASON_REQUIRED"
    })
    @DisplayName("제한 시간이 1 ~ 720시간 밖이거나 사유가 비면 400으로 거절하고 아무것도 바꾸지 않는다")
    void rejectsInvalidRestriction(String body, String resultCode) throws Exception {
        mvc.perform(json(post("/api/admin/abuse/members/7/restrictions"), body).with(admin(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value(resultCode));

        assertThat(abuseFacade.getRestriction(MEMBER).restricted()).isFalse();
    }

    @Test
    @DisplayName("실패로 멈춘 신호를 다시 판정하면 RECEIVED로 되살려 바로 판정하고, 실패 신호가 없으면 409")
    void retryFailedSignals() throws Exception {
        abuseFacade.receive(new AbuseSignalCommand(MEMBER, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED, "failed",
                LocalDateTime.now()));
        jdbcTemplate.update("UPDATE abuse_signal SET status = 'FAILED', attempt_count = 3, last_error = 'X' WHERE member_id = ?",
                MEMBER);

        ok(json(post("/api/admin/abuse/members/7/failed-signals/retry"), "{\"reason\":\"장애 복구\"}").with(admin(ADMIN)))
                .andExpect(jsonPath("$.data.resetSignalCount").value(1))
                .andExpect(jsonPath("$.data.judged").value(true));
        ok(get("/api/admin/abuse/members/7").with(admin(ADMIN)))
                .andExpect(jsonPath("$.data.signals[0].status").value("JUDGED"))
                .andExpect(jsonPath("$.data.signals[0].attemptCount").value(0))
                .andExpect(jsonPath("$.data.adminActions[0].actionType").value("RETRY_FAILED"))
                .andExpect(jsonPath("$.data.adminActions[0].affectedSignalCount").value(1));

        mvc.perform(json(post("/api/admin/abuse/members/7/failed-signals/retry"), "{\"reason\":\"다시\"}").with(admin(ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-ABUSE_NO_FAILED_SIGNAL"));
    }

    private void expired(long memberId, long sharedGroupPurchaseId) {
        abuseFacade.receive(new AbuseSignalCommand(memberId, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                "order-" + sharedGroupPurchaseId, LocalDateTime.now(),
                AbuseSignalCommand.sharedGroupPurchaseContext(sharedGroupPurchaseId)));
        abuseFacade.confirmLinks(memberId);
        abuseFacade.judgePending(memberId);
    }
}
