package com.cartmate.cartmate.boundedContext.wallet.out;

import com.cartmate.cartmate.boundedContext.wallet.domain.HoldingSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HoldingSettlementRepository extends JpaRepository<HoldingSettlement, Long> {

    List<HoldingSettlement> findAllByPayoutId(Long payoutId);

    // 홀딩에서 정산으로 꺼낸 금액의 합계. 정산 기록이 없으면 0이다.
    @Query("select coalesce(sum(s.amount), 0) from HoldingSettlement s where s.holding.id = :holdingId")
    long sumAmountByHoldingId(@Param("holdingId") Long holdingId);
}
