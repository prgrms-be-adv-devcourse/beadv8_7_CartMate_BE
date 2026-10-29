package com.cartmate.cartmate.boundedContext.trust.app;

import com.cartmate.cartmate.boundedContext.trust.domain.SellerTrustHistory;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustHistoryType;
import com.cartmate.cartmate.boundedContext.trust.out.SellerTrustHistoryRepository;
import com.cartmate.cartmate.shared.delivery.event.DeliveryClaimApprovedEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerTrustHistoryRecordUseCaseTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 29, 12, 0);

    private final SellerTrustHistoryRepository repository = mock(SellerTrustHistoryRepository.class);
    private final SellerTrustHistoryRecordUseCase useCase = new SellerTrustHistoryRecordUseCase(repository);

    private DeliveryConfirmedEvent confirmedEvent() {
        return new DeliveryConfirmedEvent(10L, 1L, 100L, null, AT.minusDays(7), AT, List.of());
    }

    private DeliveryClaimApprovedEvent claimEvent(String reason) {
        return new DeliveryClaimApprovedEvent(20L, 10L, 100L, null, 2L, 1L, "REFUND", reason, 5_000L, AT);
    }

    @Test
    @DisplayName("구매확정 이벤트는 배송 ID를 식별 키로 구매확정 이력을 남긴다")
    void recordPurchaseConfirmed() {
        useCase.recordPurchaseConfirmed(confirmedEvent());

        ArgumentCaptor<SellerTrustHistory> captor = ArgumentCaptor.forClass(SellerTrustHistory.class);
        verify(repository).save(captor.capture());
        SellerTrustHistory history = captor.getValue();
        assertThat(history.getSellerId()).isEqualTo(1L);
        assertThat(history.getType()).isEqualTo(TrustHistoryType.PURCHASE_CONFIRMED);
        assertThat(history.getSourceKey()).isEqualTo("10");
        assertThat(history.getOccurredAt()).isEqualTo(AT);
    }

    @Test
    @DisplayName("같은 배송의 구매확정 이벤트가 다시 오면 이력을 남기지 않는다")
    void purchaseConfirmedIsIdempotent() {
        when(repository.existsByTypeAndSourceKey(TrustHistoryType.PURCHASE_CONFIRMED, "10")).thenReturn(true);

        useCase.recordPurchaseConfirmed(confirmedEvent());

        verify(repository, never()).save(any());
    }

    @ParameterizedTest(name = "{0} 사유의 클레임 승인은 귀책 클레임 이력을 남긴다")
    @ValueSource(strings = {"PRODUCT_DEFECT", "WRONG_DELIVERY"})
    @DisplayName("판매자 귀책 사유의 클레임 승인은 클레임 ID를 식별 키로 귀책 클레임 이력을 남긴다")
    void recordFaultClaim(String reason) {
        useCase.recordClaimApproved(claimEvent(reason));

        ArgumentCaptor<SellerTrustHistory> captor = ArgumentCaptor.forClass(SellerTrustHistory.class);
        verify(repository).save(captor.capture());
        SellerTrustHistory history = captor.getValue();
        assertThat(history.getSellerId()).isEqualTo(1L);
        assertThat(history.getType()).isEqualTo(TrustHistoryType.FAULT_CLAIM);
        assertThat(history.getSourceKey()).isEqualTo("20");
        assertThat(history.getOccurredAt()).isEqualTo(AT);
    }

    @ParameterizedTest(name = "{0} 사유의 클레임 승인은 이력을 남기지 않는다")
    @ValueSource(strings = {"SIZE_MISMATCH", "SIMPLE_CHANGE", "OTHER"})
    @DisplayName("판매자 귀책이 아닌 사유의 클레임 승인은 이력을 남기지 않는다")
    void ignoreNonFaultClaim(String reason) {
        useCase.recordClaimApproved(claimEvent(reason));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("같은 클레임의 승인 이벤트가 다시 오면 이력을 남기지 않는다")
    void faultClaimIsIdempotent() {
        when(repository.existsByTypeAndSourceKey(TrustHistoryType.FAULT_CLAIM, "20")).thenReturn(true);

        useCase.recordClaimApproved(claimEvent("PRODUCT_DEFECT"));

        verify(repository, never()).save(any());
    }
}
