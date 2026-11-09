package com.cartmate.cartmate.boundedContext.trust.out;

import com.cartmate.cartmate.boundedContext.trust.domain.SellerTrustHistory;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustHistoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SellerTrustHistoryRepository extends JpaRepository<SellerTrustHistory, Long> {

    // 같은 이벤트를 이미 이력으로 남겼는지 확인한다. 이벤트가 두 번 와도 한 번만 반영하기 위해 쓴다.
    boolean existsByTypeAndSourceKey(TrustHistoryType type, String sourceKey);

    // 판매자마다 한 행으로 구매확정·귀책 클레임 건수를 센다. 유형별 건수를 조건부 합계로 한 행에 펼친다.
    // 한 행이 곧 판매자 한 명이라 나중에 배치로 바꿔 페이지 단위로 읽을 때도 판매자의 행이 갈라지지 않는다.
    @Query("select h.sellerId as sellerId, "
            + "sum(case when h.type = :confirmedType then 1 else 0 end) as confirmedCount, "
            + "sum(case when h.type = :faultClaimType then 1 else 0 end) as faultClaimCount "
            + "from SellerTrustHistory h group by h.sellerId order by h.sellerId")
    List<SellerTrustMetricsRow> aggregateBySeller(
            @Param("confirmedType") TrustHistoryType confirmedType,
            @Param("faultClaimType") TrustHistoryType faultClaimType);

    // 구매확정과 귀책 클레임 유형을 지정해 판매자별 건수를 센다.
    default List<SellerTrustMetricsRow> aggregateBySeller() {
        return aggregateBySeller(TrustHistoryType.PURCHASE_CONFIRMED, TrustHistoryType.FAULT_CLAIM);
    }
}
