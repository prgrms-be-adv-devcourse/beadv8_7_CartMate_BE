package com.cartmate.cartmate.boundedContext.payout.out;

import com.cartmate.cartmate.boundedContext.payout.domain.PayoutSeller;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutSellerRepository extends JpaRepository<PayoutSeller, Long> {
}
