package com.cartmate.cartmate.boundedContext.payout.app;

import com.cartmate.cartmate.boundedContext.payout.app.dto.PendingPayoutResponse;
import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementDetailResponse;
import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementInfoResponse;
import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementResponse;
import com.cartmate.cartmate.boundedContext.payout.domain.Payout;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.payout.exception.PayoutErrorCode;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository.SettlementSummary;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.member.SellerQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
                new SettlementResponse("20261115-COMPLETED", LocalDate.of(2026, 11, 15), PayoutStatus.COMPLETED,
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 18, 180_000L, 5_400L, 174_600L),
                new SettlementResponse("20261115-PENDING", LocalDate.of(2026, 11, 15), PayoutStatus.PENDING,
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 2, 20_000L, 600L, 19_400L));
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

    @Test
    @DisplayName("정산 정책과 지급 예정 금액, 지급 완료 금액을 합산해 정산 정보를 반환한다")
    void getMySettlementInfo() {
        LocalDateTime oct15 = LocalDateTime.of(2026, 10, 15, 0, 0);
        LocalDateTime nov15 = LocalDateTime.of(2026, 11, 15, 0, 0);
        SettlementSummary octCompleted = summary(oct15, PayoutStatus.COMPLETED, 3, 60_000L, 1_800L, 58_200L);
        SettlementSummary octPending = summary(oct15, PayoutStatus.PENDING, 1, 5_000L, 150L, 4_850L);
        SettlementSummary novPending = summary(nov15, PayoutStatus.PENDING, 2, 100_000L, 3_000L, 97_000L);
        SettlementSummary novCompleted = summary(nov15, PayoutStatus.COMPLETED, 4, 40_000L, 1_200L, 38_800L);
        when(payoutRepository.summarizeBySchedule(SELLER_ID, LISTED,
                LocalDate.of(2000, 1, 1).atStartOfDay(), LocalDate.of(3000, 1, 1).atStartOfDay()))
                .thenReturn(List.of(novPending, novCompleted, octCompleted, octPending));

        SettlementInfoResponse info = useCase.getMySettlementInfo(MEMBER_ID, LocalDate.of(2026, 10, 20));

        assertThat(info.feeRatePercent()).isEqualTo(3);
        assertThat(info.payoutDayOfMonth()).isEqualTo(15);
        assertThat(info.nextPayoutDate()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(info.pendingPayoutCount()).isEqualTo(3L);
        assertThat(info.pendingPayoutAmount()).isEqualTo(4_850L + 97_000L);
        assertThat(info.completedPayoutAmount()).isEqualTo(58_200L + 38_800L);
    }

    @Test
    @DisplayName("정산이 없으면 금액은 0이고 정책 정보만 반환한다")
    void getMySettlementInfoWithoutPayouts() {
        when(payoutRepository.summarizeBySchedule(any(), any(), any(), any())).thenReturn(List.of());

        SettlementInfoResponse info = useCase.getMySettlementInfo(MEMBER_ID, LocalDate.of(2026, 10, 1));

        assertThat(info.nextPayoutDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(info.pendingPayoutCount()).isZero();
        assertThat(info.pendingPayoutAmount()).isZero();
        assertThat(info.completedPayoutAmount()).isZero();
    }

    @Test
    @DisplayName("정산 정보도 판매자만 조회할 수 있다")
    void getMySettlementInfoSellerOnly() {
        when(sellerQuery.findSellerIdByMemberId(99L)).thenReturn(Optional.empty());

        assertError(() -> useCase.getMySettlementInfo(99L, LocalDate.of(2026, 10, 1)), PayoutErrorCode.PAYOUT_SELLER_ONLY);
        assertError(() -> useCase.getMySettlementInfo(null, LocalDate.of(2026, 10, 1)), PayoutErrorCode.PAYOUT_MEMBER_ID_REQUIRED);
    }

    private Payout payout(long id, String productName, Long quantity, long sales, LocalDateTime confirmedAt) {
        Payout payout = Payout.create(SELLER_ID, 100L, 1000L + id, productName, quantity, sales,
                confirmedAt.minusDays(2), confirmedAt);
        ReflectionTestUtils.setField(payout, "id", id);
        return payout;
    }

    @Test
    @DisplayName("정산 ID에 묶인 판매 건과 합계, 정산 대상 기간을 반환한다")
    void getMySettlementDetail() {
        Payout shirt = payout(1L, "티셔츠", 2L, 24_000L, LocalDateTime.of(2026, 10, 25, 10, 0));
        Payout socks = payout(2L, "양말", 3L, 5_000L, LocalDateTime.of(2026, 10, 20, 10, 0));
        when(payoutRepository.findAllBySellerIdAndStatusAndScheduledAtOrderByConfirmedAtDescIdDesc(
                SELLER_ID, PayoutStatus.COMPLETED, LocalDateTime.of(2026, 11, 15, 0, 0)))
                .thenReturn(List.of(shirt, socks));

        SettlementDetailResponse detail = useCase.getMySettlementDetail(MEMBER_ID, "20261115-COMPLETED");

        assertThat(detail.settlementId()).isEqualTo("20261115-COMPLETED");
        assertThat(detail.settlementDate()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(detail.status()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(detail.periodStart()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(detail.periodEnd()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(detail.payoutCount()).isEqualTo(2);
        assertThat(detail.totalQuantity()).isEqualTo(5);
        assertThat(detail.salesAmount()).isEqualTo(29_000L);
        assertThat(detail.feeAmount()).isEqualTo(720L + 150L);
        assertThat(detail.payoutAmount()).isEqualTo(23_280L + 4_850L);
        assertThat(detail.items()).extracting(SettlementDetailResponse.Item::productName).containsExactly("티셔츠", "양말");
        assertThat(detail.items().get(0).payoutId()).isEqualTo(1L);
        assertThat(detail.items().get(0).orderedAt()).isEqualTo(LocalDateTime.of(2026, 10, 23, 10, 0));
        assertThat(detail.items().get(0).payoutAmount()).isEqualTo(23_280L);
    }

    @Test
    @DisplayName("상품명과 수량이 기록되지 않은 옛 정산도 조회되고 수량 합계에서는 빠진다")
    void getMySettlementDetailWithLegacyPayout() {
        Payout legacy = payout(1L, null, null, 10_000L, LocalDateTime.of(2026, 10, 25, 10, 0));
        Payout recent = payout(2L, "양말", 3L, 5_000L, LocalDateTime.of(2026, 10, 20, 10, 0));
        when(payoutRepository.findAllBySellerIdAndStatusAndScheduledAtOrderByConfirmedAtDescIdDesc(any(), any(), any()))
                .thenReturn(List.of(legacy, recent));

        SettlementDetailResponse detail = useCase.getMySettlementDetail(MEMBER_ID, "20261115-PENDING");

        assertThat(detail.totalQuantity()).isEqualTo(3);
        assertThat(detail.items().get(0).productName()).isNull();
        assertThat(detail.items().get(0).quantity()).isNull();
    }

    @Test
    @DisplayName("내 정산에 해당하는 것이 없으면 PAYOUT_SETTLEMENT_NOT_FOUND 예외가 발생한다")
    void getMySettlementDetailNotFound() {
        when(payoutRepository.findAllBySellerIdAndStatusAndScheduledAtOrderByConfirmedAtDescIdDesc(any(), any(), any()))
                .thenReturn(List.of());

        assertError(() -> useCase.getMySettlementDetail(MEMBER_ID, "20261115-PENDING"),
                PayoutErrorCode.PAYOUT_SETTLEMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("정산 ID 형식이 틀리면 PAYOUT_INVALID_SETTLEMENT_ID 예외가 발생하고 조회하지 않는다")
    void getMySettlementDetailInvalidId() {
        assertError(() -> useCase.getMySettlementDetail(MEMBER_ID, "abc"), PayoutErrorCode.PAYOUT_INVALID_SETTLEMENT_ID);
        verify(payoutRepository, never())
                .findAllBySellerIdAndStatusAndScheduledAtOrderByConfirmedAtDescIdDesc(any(), any(), any());
    }

    @Test
    @DisplayName("아직 지급되지 않은 정산을 PENDING 상태로 조회해 건별 응답으로 반환한다")
    void getMyPendingPayouts() {
        Payout shirt = payout(1L, "티셔츠", 2L, 24_000L, LocalDateTime.of(2026, 10, 25, 10, 0));
        when(payoutRepository.findAllBySellerIdAndStatusOrderByScheduledAtAscConfirmedAtDescIdDesc(
                SELLER_ID, PayoutStatus.PENDING)).thenReturn(List.of(shirt));

        List<PendingPayoutResponse> result = useCase.getMyPendingPayouts(MEMBER_ID);

        assertThat(result).containsExactly(new PendingPayoutResponse(
                1L, LocalDate.of(2026, 11, 15), LocalDateTime.of(2026, 10, 23, 10, 0),
                LocalDateTime.of(2026, 10, 25, 10, 0), "티셔츠", 2L, 24_000L, 720L, 23_280L));
    }

    @Test
    @DisplayName("지급 대기 정산이 없으면 빈 목록을 반환한다")
    void getMyPendingPayoutsEmpty() {
        when(payoutRepository.findAllBySellerIdAndStatusOrderByScheduledAtAscConfirmedAtDescIdDesc(any(), any()))
                .thenReturn(List.of());

        assertThat(useCase.getMyPendingPayouts(MEMBER_ID)).isEmpty();
    }

    @Test
    @DisplayName("지급 대기 정산도 판매자만 조회할 수 있다")
    void getMyPendingPayoutsSellerOnly() {
        when(sellerQuery.findSellerIdByMemberId(99L)).thenReturn(Optional.empty());

        assertError(() -> useCase.getMyPendingPayouts(99L), PayoutErrorCode.PAYOUT_SELLER_ONLY);
        assertError(() -> useCase.getMyPendingPayouts(null), PayoutErrorCode.PAYOUT_MEMBER_ID_REQUIRED);
    }

    @Test
    @DisplayName("정산 상세도 판매자만 조회할 수 있다")
    void getMySettlementDetailSellerOnly() {
        when(sellerQuery.findSellerIdByMemberId(99L)).thenReturn(Optional.empty());

        assertError(() -> useCase.getMySettlementDetail(99L, "20261115-PENDING"), PayoutErrorCode.PAYOUT_SELLER_ONLY);
        assertError(() -> useCase.getMySettlementDetail(null, "20261115-PENDING"), PayoutErrorCode.PAYOUT_MEMBER_ID_REQUIRED);
    }
}
