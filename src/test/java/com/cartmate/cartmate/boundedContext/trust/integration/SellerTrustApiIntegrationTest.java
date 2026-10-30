package com.cartmate.cartmate.boundedContext.trust.integration;

import com.cartmate.cartmate.boundedContext.trust.domain.SellerTrustScore;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustEvaluation;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustLevel;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustMetrics;
import com.cartmate.cartmate.boundedContext.trust.out.SellerTrustScoreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 로그인 없이 실제 Security 필터와 DB를 거쳐 판매자 신뢰도 API가 공개로 동작하는지 확인한다.
@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SellerTrustApiIntegrationTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 30, 3, 0);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SellerTrustScoreRepository sellerTrustScoreRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> sellerIds = new ArrayList<>();

    private final long base = 9_600_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @AfterEach
    void tearDown() {
        sellerIds.forEach(sellerId -> jdbcTemplate.update("delete from seller_trust_score where seller_id = ?", sellerId));
    }

    @Test
    @DisplayName("로그인 없이도 저장된 판매자의 점수와 등급을 조회한다")
    void getSellerTrustWithoutLogin() throws Exception {
        long sellerId = nextSellerId();
        sellerTrustScoreRepository.save(SellerTrustScore.create(
                sellerId, new TrustMetrics(100, 5), new TrustEvaluation(90, TrustLevel.HIGH), AT));

        mvc.perform(get("/api/sellers/{sellerId}/trust", sellerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.sellerId").value(sellerId))
                .andExpect(jsonPath("$.data.score").value(90))
                .andExpect(jsonPath("$.data.level").value("HIGH"))
                .andExpect(jsonPath("$.data.confirmedCount").doesNotExist())
                .andExpect(jsonPath("$.data.faultClaimCount").doesNotExist());
    }

    @Test
    @DisplayName("점수 행이 없는 판매자는 점수 없는 NEW로 조회된다")
    void getUnratedSeller() throws Exception {
        long sellerId = nextSellerId();

        mvc.perform(get("/api/sellers/{sellerId}/trust", sellerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sellerId").value(sellerId))
                .andExpect(jsonPath("$.data.score").value(nullValue()))
                .andExpect(jsonPath("$.data.level").value("NEW"));
    }

    @Test
    @DisplayName("점수가 없는 NEW 등급으로 저장된 판매자도 score가 null로 조회된다")
    void getNewLevelSeller() throws Exception {
        long sellerId = nextSellerId();
        sellerTrustScoreRepository.save(SellerTrustScore.create(
                sellerId, new TrustMetrics(2, 0), new TrustEvaluation(null, TrustLevel.NEW), AT));

        mvc.perform(get("/api/sellers/{sellerId}/trust", sellerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(nullValue()))
                .andExpect(jsonPath("$.data.level").value("NEW"));
    }

    private long nextSellerId() {
        long sellerId = base + sequence++;
        sellerIds.add(sellerId);
        return sellerId;
    }
}
