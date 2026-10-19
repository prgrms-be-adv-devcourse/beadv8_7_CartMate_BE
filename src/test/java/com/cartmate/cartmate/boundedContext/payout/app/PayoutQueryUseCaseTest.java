package com.cartmate.cartmate.boundedContext.payout.app;

import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementResponse;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.payout.exception.PayoutErrorCode;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository.SettlementSummary;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.member.SellerQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayoutQueryUseCaseTest {

    private static final long MEMBER_ID = 20L;
    private static final long SELLER_ID = 10L;
    private static final List<PayoutStatus> LISTED = List.of(PayoutStatus.PENDING, PayoutStatus.COMPLETED);

    private final PayoutRepository payoutRepository = mock(PayoutRepository.class);
    private final SellerQuery sellerQuery = mock(SellerQuery.class);
    private PayoutQueryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PayoutQueryUseCase(payoutRepository, sellerQuery);
        when(sellerQuery.findSellerIdByMemberId(MEMBER_ID)).thenReturn(Optional.of(SELLER_ID));
    }

    private SettlementSummary summary(LocalDateTime scheduledAt, PayoutStatus status, long count,
                                      long sales, long fee, long payout) {
        SettlementSummary summary = mock(SettlementSummary.class);
        when(summary.getScheduledAt()).thenReturn(scheduledAt);
        when(summary.getStatus()).thenReturn(status);
        when(summary.getPayoutCount()).thenReturn(count);
        when(summary.getSalesAmount()).thenReturn(sales);
        when(summary.getFeeAmount()).thenReturn(fee);
        when(summary.getPayoutAmount()).thenReturn(payout);
        return summary;
    }

    private void assertError(Runnable action, PayoutErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(code.resultCode()));
    }

    @Test
    @DisplayName("판매자의 정산을 지급일과 상태별로 묶은 합계를 응답으로 바꿔 반환한다")
    void getMySettlements() {
        LocalDateTime nov15 = LocalDateTime.of(2026, 11, 15, 0, 0);
        SettlementSummary completed = summary(nov15, PayoutStatus.COMPLETED, 18, 180_000L, 5_400L, 174_600L);
        SettlementSummary pending = summary(nov15, PayoutStatus.PENDING, 2, 20_000L, 600L, 19_400L);
        when(payoutRepository.summarizeBySchedule(SELLER_ID, LISTED,
                LocalDate.of(2026, 11, 1).atStartOfDay(), LocalDate.of(2026, 12, 1).atStartOfDay()))
                .thenReturn(List.of(completed, pending));

        List<SettlementResponse> result = useCase.getMySettlements(
                MEMBER_ID, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertThat(result).containsExactly(
                new SettlementResponse(LocalDate.of(2026, 11, 15), PayoutStatus.COMPLETED, 18, 180_000L, 5_400L, 174_600L),
                new SettlementResponse(LocalDate.of(2026, 11, 15), PayoutStatus.PENDING, 2, 20_000L, 600L, 19_400L));
    }

    @Test
    @DisplayName("종료일은 그날 하루를 포함하도록 다음 날 0시 미만으로 조회한다")
    void endDateIsInclusive() {
        useCase.getMySettlements(MEMBER_ID, LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 15));

        verify(payoutRepository).summarizeBySchedule(SELLER_ID, LISTED,
                LocalDate.of(2026, 11, 15).atStartOfDay(), LocalDate.of(2026, 11, 16).atStartOfDay());
    }

    @Test
    @DisplayName("날짜를 주지 않으면 그 방향은 제한하지 않는다")
    void openRangeWhenDatesMissing() {
        useCase.getMySettlements(MEMBER_ID, null, null);

        verify(payoutRepository).summarizeBySchedule(SELLER_ID, LISTED,
                LocalDate.of(2000, 1, 1).atStartOfDay(), LocalDate.of(3000, 1, 1).atStartOfDay());
    }

    @Test
    @DisplayName("정산이 없으면 빈 목록을 반환한다")
    void empty() {
        when(payoutRepository.summarizeBySchedule(any(), any(), any(), any())).thenReturn(List.of());

        assertThat(useCase.getMySettlements(MEMBER_ID, null, null)).isEmpty();
    }

    @Test
    @DisplayName("판매자가 아니면 PAYOUT_SELLER_ONLY 예외가 발생하고 조회하지 않는다")
    void sellerOnly() {
        when(sellerQuery.findSellerIdByMemberId(99L)).thenReturn(Optional.empty());

        assertError(() -> useCase.getMySettlements(99L, null, null), PayoutErrorCode.PAYOUT_SELLER_ONLY);
        verify(payoutRepository, never()).summarizeBySchedule(any(), any(), any(), any());
    }

    @Test
    @DisplayName("회원 ID가 없으면 PAYOUT_MEMBER_ID_REQUIRED 예외가 발생한다")
    void memberIdRequired() {
        assertError(() -> useCase.getMySettlements(null, null, null), PayoutErrorCode.PAYOUT_MEMBER_ID_REQUIRED);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 PAYOUT_INVALID_DATE_RANGE 예외가 발생한다")
    void invalidDateRange() {
        assertError(() -> useCase.getMySettlements(MEMBER_ID, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1)),
                PayoutErrorCode.PAYOUT_INVALID_DATE_RANGE);
        verify(payoutRepository, never()).summarizeBySchedule(any(), any(), any(), any());
    }
}
