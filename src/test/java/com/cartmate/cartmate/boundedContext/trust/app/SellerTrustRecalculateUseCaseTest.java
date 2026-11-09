package com.cartmate.cartmate.boundedContext.trust.app;

import com.cartmate.cartmate.boundedContext.trust.domain.SellerTrustScore;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustLevel;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustMetrics;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustScorePolicy;
import com.cartmate.cartmate.boundedContext.trust.out.SellerTrustScoreRepository;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.shared.trust.event.SellerTrustScoreChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerTrustRecalculateUseCaseTest {

    private static final LocalDateTime OLD = LocalDateTime.of(2026, 10, 1, 3, 0);

    private final SellerTrustScoreRepository repository = mock(SellerTrustScoreRepository.class);
    private final EventPublisher eventPublisher = mock(EventPublisher.class);
    private final SellerTrustRecalculateUseCase useCase =
            new SellerTrustRecalculateUseCase(repository, eventPublisher);

    private SellerTrustScore existing(TrustMetrics metrics) {
        return SellerTrustScore.create(1L, metrics, TrustScorePolicy.evaluate(metrics), OLD);
    }

    @Test
    @DisplayName("점수 행이 없고 표본이 부족하면 NEW로 저장하고 변경 이벤트는 내지 않는다")
    void newSellerWithInsufficientSample() {
        when(repository.findBySellerId(1L)).thenReturn(Optional.empty());

        useCase.recalculate(1L, new TrustMetrics(2, 0));

        ArgumentCaptor<SellerTrustScore> captor = ArgumentCaptor.forClass(SellerTrustScore.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getScore()).isNull();
        assertThat(captor.getValue().getLevel()).isEqualTo(TrustLevel.NEW);
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("점수 행이 없고 점수가 나오면 저장하고 변경 이벤트를 발행한다")
    void newSellerWithScore() {
        when(repository.findBySellerId(1L)).thenReturn(Optional.empty());

        useCase.recalculate(1L, new TrustMetrics(100, 5));

        verify(repository).save(any(SellerTrustScore.class));
        ArgumentCaptor<SellerTrustScoreChangedEvent> captor = ArgumentCaptor.forClass(SellerTrustScoreChangedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().sellerId()).isEqualTo(1L);
        assertThat(captor.getValue().score()).isEqualTo(90);
        assertThat(captor.getValue().level()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("점수나 등급이 바뀌면 값을 갱신하고 변경 이벤트를 발행한다")
    void scoreChanged() {
        SellerTrustScore trustScore = existing(new TrustMetrics(100, 5));   // 90점 HIGH
        when(repository.findBySellerId(1L)).thenReturn(Optional.of(trustScore));

        useCase.recalculate(1L, new TrustMetrics(100, 20));                  // 60점 LOW

        assertThat(trustScore.getScore()).isEqualTo(60);
        assertThat(trustScore.getLevel()).isEqualTo(TrustLevel.LOW);
        assertThat(trustScore.getCalculatedAt()).isAfter(OLD);
        ArgumentCaptor<SellerTrustScoreChangedEvent> captor = ArgumentCaptor.forClass(SellerTrustScoreChangedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().score()).isEqualTo(60);
        assertThat(captor.getValue().level()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("집계 건수만 바뀌고 점수·등급이 같으면 값만 갱신하고 이벤트는 내지 않는다")
    void onlyCountsChanged() {
        SellerTrustScore trustScore = existing(new TrustMetrics(100, 5));   // 90점 HIGH
        when(repository.findBySellerId(1L)).thenReturn(Optional.of(trustScore));

        useCase.recalculate(1L, new TrustMetrics(200, 10));                  // 같은 5% → 90점 HIGH

        assertThat(trustScore.getConfirmedCount()).isEqualTo(200);
        assertThat(trustScore.getFaultClaimCount()).isEqualTo(10);
        assertThat(trustScore.getCalculatedAt()).isAfter(OLD);
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("집계 건수와 점수·등급이 모두 같으면 아무것도 바꾸지 않고 이벤트도 내지 않는다")
    void nothingChanged() {
        SellerTrustScore trustScore = existing(new TrustMetrics(100, 5));
        when(repository.findBySellerId(1L)).thenReturn(Optional.of(trustScore));

        useCase.recalculate(1L, new TrustMetrics(100, 5));

        assertThat(trustScore.getCalculatedAt()).isEqualTo(OLD);
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
}
