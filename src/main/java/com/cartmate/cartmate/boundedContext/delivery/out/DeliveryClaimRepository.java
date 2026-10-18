package com.cartmate.cartmate.boundedContext.delivery.out;

import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaim;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryClaimRepository extends JpaRepository<DeliveryClaim, Long> {
}
