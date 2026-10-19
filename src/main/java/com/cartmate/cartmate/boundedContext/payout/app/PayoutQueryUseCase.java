package com.cartmate.cartmate.boundedContext.payout.app;

import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementDetailResponse;
import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementInfoResponse;
import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementResponse;
import com.cartmate.cartmate.boundedContext.payout.domain.Payout;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutPolicy;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.payout.domain.SettlementKey;
import com.cartmate.cartmate.boundedContext.payout.exception.PayoutErrorCode;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository.SettlementSummary;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.member.SellerQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.function.ToLongFunction;

@Service
@RequiredArgsConstructor
public class PayoutQueryUseCase {

    // 판매자 화면에 보여 주는 상태. 아직 쓰지 않는 CANCELED, FAILED는 제외한다.
    private static final List<PayoutStatus> LISTED_STATUSES = List.of(PayoutStatus.PENDING, PayoutStatus.COMPLETED);

    // 날짜를 주지 않았을 때 쓰는 조회 범위의 양 끝.
    private static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2999, 12, 31);

    private final PayoutRepository payoutRepository;
    private final SellerQuery sellerQuery;

    // 로그인한 판매자의 정산 내역을 지급 예정일과 상태별로 묶어 최신 지급일부터 반환한다.
    // 시작일과 종료일은 지급 예정일 기준이며 둘 다 포함한다. 주지 않으면 그 방향은 제한하지 않는다.
    // 회원 ID가 없으면 PAYOUT_MEMBER_ID_REQUIRED, 판매자가 아니면 PAYOUT_SELLER_ONLY,
    // 시작일이 종료일보다 늦으면 PAYOUT_INVALID_DATE_RANGE 예외가 발생한다.
    public List<SettlementResponse> getMySettlements(Long memberId, LocalDate startDate, LocalDate endDate) {
        Long sellerId = sellerIdOf(memberId);

        LocalDate start = startDate == null ? MIN_DATE : startDate;
        LocalDate end = endDate == null ? MAX_DATE : endDate;

        if (start.isAfter(end)) {
            throw new ServiceException(PayoutErrorCode.PAYOUT_INVALID_DATE_RANGE);
        }

        return payoutRepository.summarizeBySchedule(
                        sellerId, LISTED_STATUSES, start.atStartOfDay(), end.plusDays(1).atStartOfDay()).stream()
                .map(SettlementResponse::from)
                .toList();
    }

    // 로그인한 판매자의 정산 정책(수수료율, 지급일, 다음 지급일)과 정산 현황(지급 예정 건수와 금액, 지급 완료 금액)을 반환한다.
    // 다음 지급일은 기준일(today) 이후로 처음 돌아오는 지급일이다.
    // 회원 ID가 없으면 PAYOUT_MEMBER_ID_REQUIRED, 판매자가 아니면 PAYOUT_SELLER_ONLY 예외가 발생한다.
    public SettlementInfoResponse getMySettlementInfo(Long memberId, LocalDate today) {
        Long sellerId = sellerIdOf(memberId);

        List<SettlementSummary> summaries = payoutRepository.summarizeBySchedule(
                sellerId, LISTED_STATUSES, MIN_DATE.atStartOfDay(), MAX_DATE.plusDays(1).atStartOfDay());

        return new SettlementInfoResponse(
                PayoutPolicy.FEE_RATE_PERCENT,
                PayoutPolicy.PAYOUT_DAY_OF_MONTH,
                PayoutPolicy.nextPayoutDate(today),
                sumOf(summaries, PayoutStatus.PENDING, SettlementSummary::getPayoutCount),
                sumOf(summaries, PayoutStatus.PENDING, SettlementSummary::getPayoutAmount),
                sumOf(summaries, PayoutStatus.COMPLETED, SettlementSummary::getPayoutAmount));
    }

    // 로그인한 판매자의 정산 한 줄(정산 ID = 지급 예정일 + 상태)의 합계와 묶인 판매 건을 반환한다.
    // 회원 ID가 없으면 PAYOUT_MEMBER_ID_REQUIRED, 판매자가 아니면 PAYOUT_SELLER_ONLY,
    // 정산 ID 형식이 틀리면 PAYOUT_INVALID_SETTLEMENT_ID, 내 정산에 해당하는 것이 없으면 PAYOUT_SETTLEMENT_NOT_FOUND 예외가 발생한다.
    public SettlementDetailResponse getMySettlementDetail(Long memberId, String settlementId) {
        Long sellerId = sellerIdOf(memberId);
        SettlementKey key = SettlementKey.parse(settlementId);

        List<Payout> payouts = payoutRepository.findAllBySellerIdAndStatusAndScheduledAtOrderByConfirmedAtDescIdDesc(
                sellerId, key.status(), key.settlementDate().atStartOfDay());

        if (payouts.isEmpty()) {
            throw new ServiceException(PayoutErrorCode.PAYOUT_SETTLEMENT_NOT_FOUND);
        }

        return SettlementDetailResponse.of(key, payouts);
    }

    // 상태가 같은 묶음의 값을 모두 더한다.
    private long sumOf(List<SettlementSummary> summaries, PayoutStatus status, ToLongFunction<SettlementSummary> value) {
        return summaries.stream()
                .filter(summary -> summary.getStatus() == status)
                .mapToLong(value)
                .sum();
    }

    // 회원 ID로 판매자 ID를 찾는다. 회원 ID가 없으면 PAYOUT_MEMBER_ID_REQUIRED, 판매자가 아니면 PAYOUT_SELLER_ONLY 예외가 발생한다.
    private Long sellerIdOf(Long memberId) {
        if (memberId == null) {
            throw new ServiceException(PayoutErrorCode.PAYOUT_MEMBER_ID_REQUIRED);
        }

        return sellerQuery.findSellerIdByMemberId(memberId)
                .orElseThrow(() -> new ServiceException(PayoutErrorCode.PAYOUT_SELLER_ONLY));
    }
}
