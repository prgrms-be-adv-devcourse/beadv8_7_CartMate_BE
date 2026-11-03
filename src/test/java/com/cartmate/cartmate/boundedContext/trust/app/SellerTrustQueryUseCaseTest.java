package com.cartmate.cartmate.boundedContext.trust.app;

import com.cartmate.cartmate.boundedContext.trust.domain.SellerTrustScore;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustEvaluation;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustLevel;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustMetrics;
import com.cartmate.cartmate.boundedContext.trust.dto.SellerTrustResponse;
import com.cartmate.cartmate.boundedContext.trust.out.SellerTrustScoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SellerTrustQueryUseCaseTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 30, 3, 0);

    private final SellerTrustScoreRepository repository = mock(SellerTrustScoreRepository.class);
    private final SellerTrustQueryUseCase useCase = new SellerTrustQueryUseCase(repository);

    @Test
    @DisplayName("점수 행이 있으면 점수와 등급을 응답한다")
    void existing() {
        when(repository.findBySellerId(1L)).thenReturn(Optional.of(
                SellerTrustScore.create(1L, new TrustMetrics(100, 5), new TrustEvaluation(90, TrustLevel.HIGH), AT)));

        assertThat(useCase.getSellerTrust(1L)).isEqualTo(new SellerTrustResponse(1L, 90, TrustLevel.HIGH));
    }

    @Test
    @DisplayName("점수가 없는 NEW 등급이면 score는 null이다")
    void newLevel() {
        when(repository.findBySellerId(3L)).thenReturn(Optional.of(
                SellerTrustScore.create(3L, new TrustMetrics(2, 0), new TrustEvaluation(null, TrustLevel.NEW), AT)));

        assertThat(useCase.getSellerTrust(3L)).isEqualTo(new SellerTrustResponse(3L, null, TrustLevel.NEW));
    }

    @Test
    @DisplayName("점수 행이 없으면 예외 없이 점수 없는 NEW로 응답한다")
    void unrated() {
        when(repository.findBySellerId(2L)).thenReturn(Optional.empty());

        assertThat(useCase.getSellerTrust(2L)).isEqualTo(new SellerTrustResponse(2L, null, TrustLevel.NEW));
    }
}
