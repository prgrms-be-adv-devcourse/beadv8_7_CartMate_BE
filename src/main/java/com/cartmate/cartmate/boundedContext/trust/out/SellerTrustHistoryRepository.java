package com.cartmate.cartmate.boundedContext.trust.out;

import com.cartmate.cartmate.boundedContext.trust.domain.SellerTrustHistory;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustHistoryType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerTrustHistoryRepository extends JpaRepository<SellerTrustHistory, Long> {

    // 같은 이벤트를 이미 이력으로 남겼는지 확인한다. 이벤트가 두 번 와도 한 번만 반영하기 위해 쓴다.
    boolean existsByTypeAndSourceKey(TrustHistoryType type, String sourceKey);
}
