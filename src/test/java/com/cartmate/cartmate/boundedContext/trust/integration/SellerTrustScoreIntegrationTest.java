package com.cartmate.cartmate.boundedContext.trust.integration;

import com.cartmate.cartmate.boundedContext.trust.domain.SellerTrustHistory;
import com.cartmate.cartmate.boundedContext.trust.domain.SellerTrustScore;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustHistoryType;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustLevel;
import com.cartmate.cartmate.boundedContext.trust.in.SellerTrustScheduler;
import com.cartmate.cartmate.boundedContext.trust.out.SellerTrustHistoryRepository;
import com.cartmate.cartmate.boundedContext.trust.out.SellerTrustMetricsRow;
import com.cartmate.cartmate.boundedContext.trust.out.SellerTrustScoreRepository;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.shared.trust.event.SellerTrustScoreChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

// 이력을 직접 저장해 두고 집계 쿼리와 스케줄러 재계산이 실제 DB에서 맞게 동작하는지 확인한다.
@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class SellerTrustScoreIntegrationTest {

    // 점수 변경 이벤트가 발행되는지만 확인하므로 실제로 내보내지 않고 목으로 대체한다.
    @MockitoBean
    private EventPublisher eventPublisher;

    @Autowired
    private SellerTrustHistoryRepository sellerTrustHistoryRepository;

    @Autowired
    private SellerTrustScoreRepository sellerTrustScoreRepository;

    @Autowired
    private SellerTrustScheduler sellerTrustScheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> sellerIds = new ArrayList<>();

    private final long base = 9_500_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L) * 100;

    private long sequence;

    @AfterEach
    void tearDown() {
        sellerIds.forEach(sellerId -> {
            jdbcTemplate.update("delete from seller_trust_history where seller_id = ?", sellerId);
            jdbcTemplate.update("delete from seller_trust_score where seller_id = ?", sellerId);
        });
    }

    @Test
    @DisplayName("판매자마다 구매확정과 귀책 클레임 건수를 한 행으로 세고, 해당 유형이 없으면 0건이다")
    void aggregateBySeller() {
        long first = nextSellerId();
        long second = nextSellerId();
        long third = nextSellerId();
        saveHistories(first, TrustHistoryType.PURCHASE_CONFIRMED, 3);
        saveHistories(first, TrustHistoryType.FAULT_CLAIM, 2);
        saveHistories(second, TrustHistoryType.PURCHASE_CONFIRMED, 4);
        saveHistories(third, TrustHistoryType.FAULT_CLAIM, 1);

        List<SellerTrustMetricsRow> rows = sellerTrustHistoryRepository.aggregateBySeller().stream()
                .filter(row -> sellerIds.contains(row.getSellerId()))
                .toList();

        assertThat(rows)
                .extracting(SellerTrustMetricsRow::getSellerId, SellerTrustMetricsRow::getConfirmedCount,
                        SellerTrustMetricsRow::getFaultClaimCount)
                .containsExactly(tuple(first, 3L, 2L), tuple(second, 4L, 0L), tuple(third, 0L, 1L));
    }

    @Test
    @DisplayName("재계산하면 점수를 저장하고, 점수가 나온 판매자만 변경 이벤트를 발행한다")
    void recalculateCreatesScores() {
        long scored = nextSellerId();
        long insufficient = nextSellerId();
        saveHistories(scored, TrustHistoryType.PURCHASE_CONFIRMED, 5);
        saveHistories(insufficient, TrustHistoryType.PURCHASE_CONFIRMED, 2);

        sellerTrustScheduler.recalculateAll();

        SellerTrustScore scoredScore = sellerTrustScoreRepository.findBySellerId(scored).orElseThrow();
        assertThat(scoredScore.getScore()).isEqualTo(100);
        assertThat(scoredScore.getLevel()).isEqualTo(TrustLevel.HIGH);
        assertThat(scoredScore.getConfirmedCount()).isEqualTo(5);
        assertThat(scoredScore.getFaultClaimCount()).isZero();

        SellerTrustScore insufficientScore = sellerTrustScoreRepository.findBySellerId(insufficient).orElseThrow();
        assertThat(insufficientScore.getScore()).isNull();
        assertThat(insufficientScore.getLevel()).isEqualTo(TrustLevel.NEW);

        assertThat(changedEventsOf(scored))
                .extracting(SellerTrustScoreChangedEvent::score, SellerTrustScoreChangedEvent::level)
                .containsExactly(tuple(100, "HIGH"));
        assertThat(changedEventsOf(insufficient)).isEmpty();
    }

    @Test
    @DisplayName("이력이 그대로이면 다시 재계산해도 저장 시각이 바뀌지 않고 이벤트도 다시 발행되지 않는다")
    void recalculateTwiceChangesNothing() {
        long sellerId = nextSellerId();
        saveHistories(sellerId, TrustHistoryType.PURCHASE_CONFIRMED, 5);
        sellerTrustScheduler.recalculateAll();
        SellerTrustScore first = sellerTrustScoreRepository.findBySellerId(sellerId).orElseThrow();

        sellerTrustScheduler.recalculateAll();

        SellerTrustScore second = sellerTrustScoreRepository.findBySellerId(sellerId).orElseThrow();
        assertThat(second.getCalculatedAt()).isEqualTo(first.getCalculatedAt());
        assertThat(second.getUpdatedAt()).isEqualTo(first.getUpdatedAt());
        assertThat(second.getVersion()).isEqualTo(first.getVersion());
        assertThat(changedEventsOf(sellerId)).hasSize(1);
    }

    @Test
    @DisplayName("이력이 쌓여 점수와 등급이 바뀌면 점수를 갱신하고 변경 이벤트를 다시 발행한다")
    void recalculateAfterNewHistories() {
        long sellerId = nextSellerId();
        saveHistories(sellerId, TrustHistoryType.PURCHASE_CONFIRMED, 5);
        sellerTrustScheduler.recalculateAll();

        saveHistories(sellerId, TrustHistoryType.FAULT_CLAIM, 1);   // 귀책 20% → 60점 LOW
        sellerTrustScheduler.recalculateAll();

        SellerTrustScore score = sellerTrustScoreRepository.findBySellerId(sellerId).orElseThrow();
        assertThat(score.getScore()).isEqualTo(60);
        assertThat(score.getLevel()).isEqualTo(TrustLevel.LOW);
        assertThat(score.getFaultClaimCount()).isEqualTo(1);
        assertThat(changedEventsOf(sellerId))
                .extracting(SellerTrustScoreChangedEvent::score, SellerTrustScoreChangedEvent::level)
                .containsExactly(tuple(100, "HIGH"), tuple(60, "LOW"));
    }

    @Test
    @DisplayName("점수나 등급은 그대로이고 집계 건수만 바뀌면 값은 갱신하되 이벤트는 발행하지 않는다")
    void recalculateWhenOnlyCountsChange() {
        long sellerId = nextSellerId();
        saveHistories(sellerId, TrustHistoryType.PURCHASE_CONFIRMED, 5);
        sellerTrustScheduler.recalculateAll();

        saveHistories(sellerId, TrustHistoryType.PURCHASE_CONFIRMED, 3);   // 여전히 귀책 0건 → 100점 HIGH
        sellerTrustScheduler.recalculateAll();

        SellerTrustScore score = sellerTrustScoreRepository.findBySellerId(sellerId).orElseThrow();
        assertThat(score.getScore()).isEqualTo(100);
        assertThat(score.getConfirmedCount()).isEqualTo(8);
        assertThat(changedEventsOf(sellerId)).hasSize(1);
    }

    private void saveHistories(long sellerId, TrustHistoryType type, int count) {
        for (int i = 0; i < count; i++) {
            sellerTrustHistoryRepository.save(SellerTrustHistory.create(
                    sellerId, type, UUID.randomUUID().toString(), LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)));
        }
    }

    private List<SellerTrustScoreChangedEvent> changedEventsOf(long sellerId) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeast(0)).publish(captor.capture());
        return captor.getAllValues().stream()
                .filter(SellerTrustScoreChangedEvent.class::isInstance)
                .map(SellerTrustScoreChangedEvent.class::cast)
                .filter(event -> event.sellerId().equals(sellerId))
                .toList();
    }

    private long nextSellerId() {
        long sellerId = base + sequence++;
        sellerIds.add(sellerId);
        return sellerId;
    }
}
