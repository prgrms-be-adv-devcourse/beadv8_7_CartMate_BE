package com.cartmate.cartmate.boundedContext.payout.out;

import com.cartmate.cartmate.boundedContext.payout.domain.Payout;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutPurchaseType;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PayoutRepository extends JpaRepository<Payout, Long> {

    List<Payout> findAllByOrderItemIdIn(Collection<Long> orderItemIds);

    // 개인 주문과 공동물품 주문은 주문상품 ID가 겹칠 수 있어 구매 유형까지 같이 조회한다.
    List<Payout> findAllByPurchaseTypeAndOrderItemIdIn(PayoutPurchaseType purchaseType, Collection<Long> orderItemIds);

    // 판매자의 정산을 지급 예정 시각과 상태별로 묶어 건수와 금액 합계를 구한다. 지급 예정 시각이 fromAt 이상 toAt 미만인 정산만 대상이며 최신 지급일부터 정렬한다.
    @Query("select p.scheduledAt as scheduledAt, p.status as status, count(p) as payoutCount, "
            + "sum(p.salesAmount) as salesAmount, sum(p.feeAmount) as feeAmount, sum(p.payoutAmount) as payoutAmount "
            + "from Payout p where p.sellerId = :sellerId and p.status in :statuses "
            + "and p.scheduledAt >= :fromAt and p.scheduledAt < :toAt "
            + "group by p.scheduledAt, p.status order by p.scheduledAt desc, p.status")
    List<SettlementSummary> summarizeBySchedule(
            @Param("sellerId") Long sellerId, @Param("statuses") Collection<PayoutStatus> statuses,
            @Param("fromAt") LocalDateTime fromAt, @Param("toAt") LocalDateTime toAt);

    // 판매자의 정산 중 지급 예정 시각과 상태가 같은 것을 구매확정이 최근인 순으로 조회한다. 정산 상세의 판매 건 목록이다.
    List<Payout> findAllBySellerIdAndStatusAndScheduledAtOrderByConfirmedAtDescIdDesc(
            Long sellerId, PayoutStatus status, LocalDateTime scheduledAt);

    // 판매자의 정산 중 상태가 같은 것을 지급 예정이 이른 순, 같으면 구매확정이 최근인 순으로 조회한다.
    List<Payout> findAllBySellerIdAndStatusOrderByScheduledAtAscConfirmedAtDescIdDesc(Long sellerId, PayoutStatus status);

    // 지급 예정 시각과 상태가 같은 정산을 묶은 합계.
    interface SettlementSummary {

        LocalDateTime getScheduledAt();

        PayoutStatus getStatus();

        Long getPayoutCount();

        Long getSalesAmount();

        Long getFeeAmount();

        Long getPayoutAmount();
    }

    // 지급 예정 시각이 지난 대기 정산의 ID를 lastId 다음부터 ID 순으로 조회한다. 지급 배치가 페이지 단위로 읽는 데 쓴다.
    @Query("select p.id from Payout p where p.status = :status and p.scheduledAt <= :now and p.id > :lastId order by p.id")
    List<Long> findDueIdsAfter(
            @Param("status") PayoutStatus status, @Param("now") LocalDateTime now,
            @Param("lastId") Long lastId, Pageable pageable);

    // 정산 행을 잠그고 조회한다. 지급이 동시에 들어와도 한 건만 처리되도록 트랜잭션 안에서 호출한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payout p where p.id = :id")
    Optional<Payout> findByIdForUpdate(@Param("id") Long id);
}
