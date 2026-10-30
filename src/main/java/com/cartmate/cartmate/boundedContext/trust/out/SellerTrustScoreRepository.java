package com.cartmate.cartmate.boundedContext.trust.out;

import com.cartmate.cartmate.boundedContext.trust.domain.SellerTrustScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SellerTrustScoreRepository extends JpaRepository<SellerTrustScore, Long> {

    // 판매자의 신뢰도 점수를 조회한다. 거래 이력이 아직 없는 판매자는 점수 행이 없다.
    Optional<SellerTrustScore> findBySellerId(Long sellerId);
}
