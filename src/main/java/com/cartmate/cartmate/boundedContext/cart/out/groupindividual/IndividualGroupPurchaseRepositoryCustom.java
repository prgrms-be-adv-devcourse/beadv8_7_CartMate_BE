package com.cartmate.cartmate.boundedContext.cart.out.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchase;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public interface IndividualGroupPurchaseRepositoryCustom {

    Optional<IndividualGroupPurchase> findByIdForUpdate(Long id);

    Optional<IndividualGroupPurchase> findWithProductsByInviteCode(String inviteCode);

    Optional<IndividualGroupPurchase> findWithProductsById(Long id);

    Optional<IndividualGroupPurchase> findByDemandProductKeyForUpdate(Long productId);

    Map<Long, Long> findOpenDemandPurchaseIds(Collection<Long> productIds, LocalDateTime now);

    // 마감 시각이 지났는데 아직 OPEN인 어드민 공동구매 (시스템 마감 대상)
    List<Long> findDemandIdsToCloseByDeadline(LocalDateTime now);

    List<Long> findDemandProductIdsEndedBetween(LocalDateTime since, LocalDateTime now);

    List<IndividualGroupPurchase> findOpenDemandPurchases(LocalDateTime now);
}
