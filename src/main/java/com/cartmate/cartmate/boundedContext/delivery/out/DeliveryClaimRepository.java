package com.cartmate.cartmate.boundedContext.delivery.out;

import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface DeliveryClaimRepository extends JpaRepository<DeliveryClaim, Long> {

    boolean existsByDeliveryIdAndStatusIn(Long deliveryId, Collection<ClaimStatus> statuses);
}
