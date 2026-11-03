package com.cartmate.cartmate.boundedContext.trust.integration;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSellerTrust;
import com.cartmate.cartmate.boundedContext.post.domain.SellerTrustLevel;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostSellerTrustRepository;
import com.cartmate.cartmate.boundedContext.trust.domain.SellerTrustHistory;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustHistoryType;
import com.cartmate.cartmate.boundedContext.trust.in.SellerTrustScheduler;
import com.cartmate.cartmate.boundedContext.trust.out.SellerTrustHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

// trust가 점수를 계산해 변경 이벤트를 발행하면, post가 커밋 뒤 판매자 신뢰도 복제본을 만들고 갱신하는지 실제 흐름으로 확인한다.
@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class SellerTrustToPostIntegrationTest {

    @Autowired
    private SellerTrustHistoryRepository sellerTrustHistoryRepository;

    @Autowired
    private SellerTrustScheduler sellerTrustScheduler;

    @Autowired
    private SalesPostSellerTrustRepository salesPostSellerTrustRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> sellerIds = new ArrayList<>();

    private final long base = 9_700_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @AfterEach
    void tearDown() {
        sellerIds.forEach(sellerId -> {
            jdbcTemplate.update("delete from seller_trust_history where seller_id = ?", sellerId);
            jdbcTemplate.update("delete from seller_trust_score where seller_id = ?", sellerId);
            jdbcTemplate.update("delete from post_seller_trust where seller_id = ?", sellerId);
        });
    }

    @Test
    @DisplayName("점수가 계산되면 post에 판매자 신뢰도 복제본이 만들어진다")
    void scoreIsReplicatedToPost() {
        long sellerId = nextSellerId();
        saveHistories(sellerId, TrustHistoryType.PURCHASE_CONFIRMED, 5);

        sellerTrustScheduler.recalculateAll();

        SalesPostSellerTrust trust = salesPostSellerTrustRepository.findById(sellerId).orElseThrow();
        assertThat(trust.getScore()).isEqualTo(100);
        assertThat(trust.getLevel()).isEqualTo(SellerTrustLevel.HIGH);
    }

    @Test
    @DisplayName("점수가 없는 NEW 판매자는 변경이 없어 복제본이 만들어지지 않는다")
    void newSellerIsNotReplicated() {
        long sellerId = nextSellerId();
        saveHistories(sellerId, TrustHistoryType.PURCHASE_CONFIRMED, 2);

        sellerTrustScheduler.recalculateAll();

        assertThat(salesPostSellerTrustRepository.findById(sellerId)).isEmpty();
    }

    @Test
    @DisplayName("점수와 등급이 바뀌면 post의 복제본도 갱신된다")
    void changedScoreIsReplicated() {
        long sellerId = nextSellerId();
        saveHistories(sellerId, TrustHistoryType.PURCHASE_CONFIRMED, 5);
        sellerTrustScheduler.recalculateAll();

        saveHistories(sellerId, TrustHistoryType.FAULT_CLAIM, 1);   // 귀책 20% → 60점 LOW
        sellerTrustScheduler.recalculateAll();

        SalesPostSellerTrust trust = salesPostSellerTrustRepository.findById(sellerId).orElseThrow();
        assertThat(trust.getScore()).isEqualTo(60);
        assertThat(trust.getLevel()).isEqualTo(SellerTrustLevel.LOW);
    }

    @Test
    @DisplayName("다시 계산해도 변화가 없으면 post의 복제본은 그대로다")
    void unchangedScoreKeepsReplica() {
        long sellerId = nextSellerId();
        saveHistories(sellerId, TrustHistoryType.PURCHASE_CONFIRMED, 5);
        sellerTrustScheduler.recalculateAll();
        SalesPostSellerTrust first = salesPostSellerTrustRepository.findById(sellerId).orElseThrow();

        sellerTrustScheduler.recalculateAll();

        SalesPostSellerTrust second = salesPostSellerTrustRepository.findById(sellerId).orElseThrow();
        assertThat(second.getCalculatedAt()).isEqualTo(first.getCalculatedAt());
        assertThat(second.getUpdatedAt()).isEqualTo(first.getUpdatedAt());
    }

    private void saveHistories(long sellerId, TrustHistoryType type, int count) {
        for (int i = 0; i < count; i++) {
            sellerTrustHistoryRepository.save(SellerTrustHistory.create(
                    sellerId, type, UUID.randomUUID().toString(), LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)));
        }
    }

    private long nextSellerId() {
        long sellerId = base + sequence++;
        sellerIds.add(sellerId);
        return sellerId;
    }
}
